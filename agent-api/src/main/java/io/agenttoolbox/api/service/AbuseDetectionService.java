package io.agenttoolbox.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Detects abusive patterns: rapid-fire queries (&gt;5/min) and identical repeated messages.
 * Uses in-memory tracking per user. Auto-throttles abusive users.
 *
 * <p>Tracked state is evicted after the throttle window expires.
 * This is lightweight and sufficient for single-instance deployments.
 * For multi-instance, move to Redis or a shared store.</p>
 *
 * <p>Stale entries are evicted periodically to prevent unbounded memory growth.</p>
 */
@Service
public class AbuseDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AbuseDetectionService.class);

    private static final int MAX_QUERIES_PER_MINUTE = 5;
    private static final int MAX_IDENTICAL_MESSAGES = 3;
    private static final long THROTTLE_WINDOW_SECONDS = 60;
    private static final long IDENTICAL_MSG_WINDOW_SECONDS = 300; // 5 minutes
    private static final int EVICTION_INTERVAL = 50; // evict every N calls

    /** Per-user timestamps of recent queries (sliding window). */
    private final Map<UUID, Deque<Instant>> queryTimestamps = new ConcurrentHashMap<>();

    /** Per-user recent messages for duplicate detection. */
    private final Map<UUID, Deque<TimestampedMessage>> recentMessages = new ConcurrentHashMap<>();

    /** Users currently throttled and when the throttle expires. */
    private final Map<UUID, Instant> throttledUsers = new ConcurrentHashMap<>();

    private int callCounter = 0;

    /**
     * Checks for abuse patterns and records the current request.
     *
     * @param userId  the user making the request
     * @param message the message content
     * @return null if allowed, or an error message if abusive pattern detected
     */
    public String checkAndRecord(UUID userId, String message) {
        Instant now = Instant.now();

        // Check if user is currently throttled
        Instant throttleExpiry = throttledUsers.get(userId);
        if (throttleExpiry != null) {
            if (now.isBefore(throttleExpiry)) {
                return "You've been temporarily throttled due to unusual activity. "
                        + "Please wait a moment before trying again.";
            }
            throttledUsers.remove(userId);
        }

        // Check rapid-fire: >5 queries per minute
        Deque<Instant> timestamps = queryTimestamps.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        Instant oneMinuteAgo = now.minusSeconds(THROTTLE_WINDOW_SECONDS);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(oneMinuteAgo)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_QUERIES_PER_MINUTE) {
            log.warn("Rapid-fire detected for userId={}: {} queries in last minute", userId, timestamps.size());
            throttledUsers.put(userId, now.plusSeconds(THROTTLE_WINDOW_SECONDS));
            return "Too many requests. Please slow down and try again in a minute.";
        }
        timestamps.addLast(now);

        // Check identical messages within window
        Deque<TimestampedMessage> messages = recentMessages.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        Instant windowStart = now.minusSeconds(IDENTICAL_MSG_WINDOW_SECONDS);
        while (!messages.isEmpty() && messages.peekFirst().timestamp.isBefore(windowStart)) {
            messages.pollFirst();
        }

        String normalized = message.trim().toLowerCase();
        long identicalCount = messages.stream()
                .filter(m -> m.content.equals(normalized))
                .count();

        if (identicalCount >= MAX_IDENTICAL_MESSAGES) {
            log.warn("Repeated message detected for userId={}: '{}' sent {} times",
                    userId, normalized.substring(0, Math.min(50, normalized.length())), identicalCount);
            throttledUsers.put(userId, now.plusSeconds(THROTTLE_WINDOW_SECONDS));
            return "Duplicate message detected. Please try a different question.";
        }
        messages.addLast(new TimestampedMessage(now, normalized));

        // Periodically evict stale user entries to prevent memory leaks
        if (++callCounter % EVICTION_INTERVAL == 0) {
            evictStaleEntries(now);
        }

        return null;
    }

    private void evictStaleEntries(Instant now) {
        // Evict expired throttles
        throttledUsers.entrySet().removeIf(e -> now.isAfter(e.getValue()));

        // Evict empty query timestamp deques
        Instant oneMinuteAgo = now.minusSeconds(THROTTLE_WINDOW_SECONDS);
        Iterator<Map.Entry<UUID, Deque<Instant>>> tsIt = queryTimestamps.entrySet().iterator();
        while (tsIt.hasNext()) {
            Deque<Instant> ts = tsIt.next().getValue();
            while (!ts.isEmpty() && ts.peekFirst().isBefore(oneMinuteAgo)) {
                ts.pollFirst();
            }
            if (ts.isEmpty()) {
                tsIt.remove();
            }
        }

        // Evict empty message deques
        Instant windowStart = now.minusSeconds(IDENTICAL_MSG_WINDOW_SECONDS);
        Iterator<Map.Entry<UUID, Deque<TimestampedMessage>>> msgIt = recentMessages.entrySet().iterator();
        while (msgIt.hasNext()) {
            Deque<TimestampedMessage> msgs = msgIt.next().getValue();
            while (!msgs.isEmpty() && msgs.peekFirst().timestamp.isBefore(windowStart)) {
                msgs.pollFirst();
            }
            if (msgs.isEmpty()) {
                msgIt.remove();
            }
        }
    }

    private record TimestampedMessage(Instant timestamp, String content) {}
}
