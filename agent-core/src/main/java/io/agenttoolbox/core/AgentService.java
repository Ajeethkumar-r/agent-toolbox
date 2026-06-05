package io.agenttoolbox.core;

import dev.langchain4j.service.SystemMessage;

public interface AgentService {

    @SystemMessage(fromResource = "prompts/system-prompt.txt")
    String chat(String userMessage);
}
