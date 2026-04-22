package com.example.bookmyshowoct24.agent;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Collects every AgentTool @Component at startup and exposes two views:
 *   - lookup(name)        -> the Spring bean, for execution
 *   - toAnthropicTools()  -> Anthropic SDK Tool objects, for the API request
 *
 * Spring auto-injects the full list of beans implementing AgentTool.
 */
@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> toolList) {
        // Build a name -> tool lookup so the agent loop can find a tool in O(1)
        // by the name Claude returns in its tool_use block.
        Map<String, AgentTool> byName = new HashMap<>();
        for (AgentTool tool : toolList) {
            byName.put(tool.getName(), tool);
        }
        this.tools = byName;
    }

    public AgentTool lookup(String name) {
        return tools.get(name);
    }

    // Convert each AgentTool into an Anthropic SDK Tool (name + description + schema)
    // so we can send the full catalogue with every API request.
    public List<Tool> toAnthropicTools() {
        List<Tool> result = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            result.add(toAnthropicTool(tool));
        }
        return result;
    }

    //like this schema that is for anthropic we will have to write different function code for different models to match there API call format. Like eg - OpenAi , Gemini etc.
    @SuppressWarnings("unchecked")
    private Tool toAnthropicTool(AgentTool tool) {
        Map<String, Object> schema = tool.getInputSchema();

        Tool.InputSchema.Properties.Builder propsBuilder = Tool.InputSchema.Properties.builder();
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            propsBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
        }

        List<String> required = (List<String>) schema.getOrDefault("required", List.of());

        return Tool.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .inputSchema(Tool.InputSchema.builder()
                        .properties(propsBuilder.build())
                        .required(required)
                        .build())
                .build();
    }
}
