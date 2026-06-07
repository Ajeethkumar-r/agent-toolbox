package io.agenttoolbox.api.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitizes user chat messages before processing.
 * Strips HTML tags and detects suspicious prompt injection patterns.
 */
@Component
public class InputSanitizer {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignore previous instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore all previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal your instructions", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Sanitizes the input by stripping HTML tags and trimming whitespace.
     *
     * @param input the raw user input
     * @return sanitized input
     */
    public String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String stripped = HTML_TAG_PATTERN.matcher(input).replaceAll("");
        return stripped.trim();
    }

    /**
     * Checks whether the input contains known prompt injection patterns.
     * Does NOT block the input -- only flags it for logging.
     *
     * @param input the user input to check
     * @return true if any suspicious pattern is detected
     */
    public boolean containsSuspiciousPatterns(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
}
