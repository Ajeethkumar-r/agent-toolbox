package io.agenttoolbox.core.model;

public record AgentResponse(String message, boolean success) {

    public static AgentResponse ok(String message) {
        return new AgentResponse(message, true);
    }

    public static AgentResponse error(String message) {
        return new AgentResponse(message, false);
    }
}
