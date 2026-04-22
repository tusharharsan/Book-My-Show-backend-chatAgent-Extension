package com.example.bookmyshowoct24.agent;

import java.util.Map;

/*
 * Contract every agent tool implements. Each tool wraps one service method
 * so Claude can call it autonomously during a conversation.
 *
 * Example: SearchMoviesTool wraps MovieService.searchMovies.
 */
public interface AgentTool {

    String getName();

    String getDescription();

    // JSON schema describing the tool's input parameters.
    // Claude reads this schema to know which arguments to pass when calling the tool.
    Map<String, Object> getInputSchema();

    // Execute the tool with arguments Claude provided. Returns a string
    // (usually JSON) that becomes the tool_result block in the next turn.
    String execute(Map<String, Object> input);
}
