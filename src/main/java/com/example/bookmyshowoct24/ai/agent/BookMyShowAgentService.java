package com.example.bookmyshowoct24.ai.agent;

import com.example.bookmyshowoct24.chat.ChatRequestContext;
import com.example.bookmyshowoct24.ai.tools.DiscoveryTools;
import com.example.bookmyshowoct24.ai.tools.PaymentTools;
import com.example.bookmyshowoct24.ai.tools.TicketingTools;
import com.example.bookmyshowoct24.ai.tools.UserTools;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class BookMyShowAgentService {

    private static final Logger log = LoggerFactory.getLogger(BookMyShowAgentService.class);

    private static final int MEMORY_WINDOW = 40;

    private final ChatClient geminiClient;
    private final ChatClient anthropicClient;
    private final ChatClient openAiClient;
    private final ChatClient ollamaClient;

    public BookMyShowAgentService(
            // ---- The four provider ChatModels, wired by @Qualifier ---------------------
            // NOTE the Vertex AI bean name quirk: it is "vertexAiGeminiChat", NOT
            // "vertexAiGeminiChatModel" like the other three. Verified by decompiling
            // VertexAiGeminiChatAutoConfiguration.
            @Qualifier("vertexAiGeminiChat") ChatModel geminiModel,
            @Qualifier("anthropicChatModel") ChatModel anthropicModel,
            @Qualifier("openAiChatModel") ChatModel openAiModel,
            @Qualifier("ollamaChatModel") ChatModel ollamaModel,

            // ---- The four tool facades from the ai.tools package ----------------------
            DiscoveryTools discoveryTools,
            TicketingTools ticketingTools,
            PaymentTools paymentTools,
            UserTools userTools,

            // ---- System prompt loaded from classpath ----------------------------------
            @Value("classpath:/prompts/bookbot-system.st") Resource systemPromptResource
    ) {
        String systemPrompt = readResource(systemPromptResource);

        // ONE shared ChatMemory across all four clients — so conversation history
        // survives a failover: when Anthropic takes over mid-session, it sees the
        // Gemini-side conversation intact. MessageWindowChatMemory keeps the last N
        // messages per conversationId; we supply conversationId per call as sessionId.
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(MEMORY_WINDOW)
                .build();
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        // Bundle all four tool facades into a single varargs array. Spring AI reflects
        // over each one to discover its @Tool-annotated methods at call time.
        Object[] toolbox = new Object[]{discoveryTools, ticketingTools, paymentTools, userTools};

        // Build one ChatClient per provider. Note the static factory:
        // ChatClient.builder(ChatModel) — NOT the injectable ChatClient.Builder,
        // which is pre-bound to a single ChatModel and can't be re-targeted.
        this.geminiClient = buildClient(geminiModel, systemPrompt, memoryAdvisor, toolbox);
        this.anthropicClient = buildClient(anthropicModel, systemPrompt, memoryAdvisor, toolbox);
        this.openAiClient = buildClient(openAiModel, systemPrompt, memoryAdvisor, toolbox);
        this.ollamaClient = buildClient(ollamaModel, systemPrompt, memoryAdvisor, toolbox);

        log.info("BookMyShowAgentService initialised — 4 ChatClients ready (Gemini primary).");
    }

    // ===========================================================================
    //   PUBLIC ENTRY POINT — called by AgentController (REST) and ChatWebSocketHandler.
    //
    //   Flow on success (happy path):
    //     processMessage → Gemini responds → return content to user.
    //
    //   Flow on primary failure:
    //     processMessage throws → @Retry's AOP proxy catches → invokes fallbackChain
    //     → Anthropic answers → return to user (user sees one reply, never knows).
    //
    //   Flow on total outage:
    //     processMessage throws → fallbackChain → Anthropic throws → OpenAI throws
    //     → Ollama throws → polite "sorry, try again" string to the user.
    // ===========================================================================
    @Retry(name = "aiModelFailover", fallbackMethod = "fallbackChain")
    public String processMessage(String sessionId, Long userId, String userText) {
        // IMPORTANT: no try/finally around ChatRequestContext.clear() here.
        //
        // Why: if the primary call throws, @Retry's AOP interceptor needs to call
        // fallbackChain() which ALSO runs tools on this same thread and needs the
        // same userId/sessionId visible. A finally-clear would wipe the ThreadLocal
        // BEFORE the fallback runs. The fallback re-sets them from its own args and
        // clears them itself, so the lifetime is: set here → survive through Gemini
        // call → (if Gemini failed) survive through fallback chain → cleared there.
        ChatRequestContext.setUserId(userId);
        ChatRequestContext.setSessionId(sessionId);
        try {
            String reply = callClient(geminiClient, sessionId, userText, "Gemini");
            // Success path owns its own cleanup — the fallback path does the same.
            ChatRequestContext.clear();
            return reply;
        } catch (RuntimeException rethrown) {
            // Let it propagate to Resilience4j so the fallback fires.
            // We deliberately do NOT clear ChatRequestContext here — the fallback needs it.
            throw rethrown;
        }
    }

    /**
     * Resilience4j fallback — signature MUST match the annotated method's arguments
     * plus a trailing {@link Throwable}. If the signature drifts, Resilience4j silently
     * ignores it and the original exception is thrown. (Common beginner gotcha.)
     *
     * <p>We do the Anthropic → OpenAI → Ollama cascade with nested try/catch rather
     * than stacked {@code @Retry} annotations, because Spring AOP doesn't intercept
     * self-invocations inside the same bean — the second annotation would silently
     * do nothing.
     */
    public String fallbackChain(String sessionId, Long userId, String userText, Throwable primaryFailure) {
        log.warn("[session={}] Gemini failed: {} — entering fallback chain.",
                sessionId, primaryFailure.toString());

        // Re-seat ChatRequestContext — see note in processMessage about why.
        ChatRequestContext.setUserId(userId);
        ChatRequestContext.setSessionId(sessionId);
        try {
            try {
                return callClient(anthropicClient, sessionId, userText, "Anthropic");
            } catch (Exception anthropicFailure) {
                log.warn("[session={}] Anthropic failed: {} — trying OpenAI.",
                        sessionId, anthropicFailure.toString());
                try {
                    return callClient(openAiClient, sessionId, userText, "OpenAI");
                } catch (Exception openAiFailure) {
                    log.warn("[session={}] OpenAI failed: {} — trying Ollama.",
                            sessionId, openAiFailure.toString());
                    try {
                        return callClient(ollamaClient, sessionId, userText, "Ollama");
                    } catch (Exception ollamaFailure) {
                        // All four providers down — surface a user-friendly message
                        // rather than a raw stack trace. Original cause is already logged.
                        log.error("[session={}] All four providers failed. Primary cause: ",
                                sessionId, primaryFailure);
                        return "Sorry — all of my AI providers are currently unreachable. "
                                + "Please try again in a minute.";
                    }
                }
            }
        } finally {
            ChatRequestContext.clear();
        }
    }

    // ===========================================================================
    //   Private helpers.
    // ===========================================================================

    /**
     * Single call to a {@link ChatClient}. The {@code label} parameter exists purely
     * for logging so we can see in the logs which provider actually answered — useful
     * when debugging why the user got a reply even though Gemini is "down".
     */
    private String callClient(ChatClient client, String sessionId, String userText, String label) {
        log.info("[session={}] -> {}", sessionId, label);
        return client.prompt()
                .user(userText)
                // CONVERSATION_ID tells the memory advisor which conversation's history
                // to pre-pend to this call. Without it, every turn would look like the
                // start of a brand-new chat.
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
    }

    /** Identical ChatClient wiring across providers — so UX doesn't change on failover. */
    private ChatClient buildClient(ChatModel model,
                                   String systemPrompt,
                                   MessageChatMemoryAdvisor memoryAdvisor,
                                   Object... tools) {
        return ChatClient.builder(model)
                .defaultSystem(systemPrompt)
                .defaultTools(tools)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    private static String readResource(Resource resource) {
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load system prompt from " + resource + ". "
                            + "Check that src/main/resources/prompts/bookbot-system.st exists.", e);
        }
    }
}
