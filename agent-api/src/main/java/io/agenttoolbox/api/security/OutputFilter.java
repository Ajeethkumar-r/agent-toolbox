package io.agenttoolbox.api.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Filters sensitive content from LLM responses before sending to the client.
 * Redacts API keys, internal paths, connection strings, and JWT tokens.
 */
@Component
public class OutputFilter {

    private static final String REDACTED = "[REDACTED]";

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            // API key patterns: AIza..., sk-..., GEMINI_API_KEY=...
            Pattern.compile("AIza[0-9A-Za-z_-]{30,}"),
            Pattern.compile("sk-[0-9A-Za-z_-]{20,}"),
            Pattern.compile("(?i)(GEMINI_API_KEY|OPENAI_API_KEY|API_KEY)\\s*=\\s*\\S+"),

            // Internal filesystem paths
            Pattern.compile("/Users/[^\\s\"'`,;)}>]+"),
            Pattern.compile("/home/[^\\s\"'`,;)}>]+"),
            Pattern.compile("/opt/[^\\s\"'`,;)}>]+"),

            // Connection strings
            Pattern.compile("jdbc:postgresql://[^\\s\"'`,;)}>]+"),
            Pattern.compile("postgresql://[^\\s\"'`,;)}>]+"),

            // JWT tokens (base64-encoded, starting with eyJ)
            Pattern.compile("eyJ[A-Za-z0-9_-]{20,}\\.eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}")
    );

    /**
     * Scans the LLM response and redacts any sensitive content.
     *
     * @param response the raw LLM response
     * @return the filtered response with sensitive patterns replaced by [REDACTED]
     */
    public String filter(String response) {
        if (response == null) {
            return "";
        }
        String filtered = response;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            filtered = pattern.matcher(filtered).replaceAll(REDACTED);
        }
        return filtered;
    }
}
