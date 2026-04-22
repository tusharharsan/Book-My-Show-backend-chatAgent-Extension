package com.example.bookmyshowoct24.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicClientConfig {

    // Builds one shared AnthropicClient for the whole app.
    // API key is read from the ANTHROPIC_API_KEY env var (preferred) or
    // application.properties (via ${anthropic.api.key:}).
    @Bean
    public AnthropicClient anthropicClient(@Value("${anthropic.api.key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            // Falls back to env var ANTHROPIC_API_KEY automatically.
            return AnthropicOkHttpClient.fromEnv();
        }
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }
}
