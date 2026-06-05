package io.agenttoolbox.cli;

import io.agenttoolbox.core.model.AgentResponse;

public class OutputFormatter {

    private final VerbosityMode mode;

    public OutputFormatter(VerbosityMode mode) {
        this.mode = mode;
    }

    public String format(AgentResponse response) {
        if (!response.success()) {
            return "ERROR: " + response.message();
        }
        return response.message();
    }
}
