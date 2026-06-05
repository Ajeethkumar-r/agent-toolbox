package io.agenttoolbox.cli;

import io.agenttoolbox.core.model.AgentResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputFormatterTest {

    @Test
    void formatsSuccessResponse() {
        OutputFormatter formatter = new OutputFormatter(VerbosityMode.CONCISE);
        AgentResponse response = AgentResponse.ok("Synced 5 files");
        assertThat(formatter.format(response)).isEqualTo("Synced 5 files");
    }

    @Test
    void formatsErrorResponseWithPrefix() {
        OutputFormatter formatter = new OutputFormatter(VerbosityMode.CONCISE);
        AgentResponse response = AgentResponse.error("File not found");
        assertThat(formatter.format(response)).startsWith("ERROR: ");
    }
}
