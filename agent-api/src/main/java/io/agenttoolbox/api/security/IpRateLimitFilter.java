package io.agenttoolbox.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * IP-based rate limiting filter. Limits each IP to a configurable number
 * of requests per hour using an in-memory sliding window.
 *
 * <p>Applied before authentication so it catches unauthenticated abuse too.
 * Only applies to /api/ endpoints to avoid blocking health checks.</p>
 *
 * <p>Stale entries are evicted periodically during request processing
 * to prevent unbounded memory growth.</p>
 */
@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitFilter.class);
    private static final long WINDOW_SECONDS = 3600; // 1 hour
    private static final int EVICTION_INTERVAL = 100; // evict every N requests

    @Value("${rate-limit.ip-requests-per-hour:100}")
    private int maxRequestsPerHour;

    private final Map<String, Deque<Instant>> requestTimestamps = new ConcurrentHashMap<>();
    private int requestCounter = 0;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only rate-limit API endpoints
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        Deque<Instant> timestamps = requestTimestamps.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());

        // Evict expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequestsPerHour) {
            log.warn("IP rate limit exceeded for {}: {} requests in last hour", clientIp, timestamps.size());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"Too many requests from this IP. Please try again later.\",\"status\":429}");
            return;
        }

        timestamps.addLast(now);

        // Periodically evict stale IP entries to prevent memory leaks
        if (++requestCounter % EVICTION_INTERVAL == 0) {
            evictStaleEntries(now);
        }

        filterChain.doFilter(request, response);
    }

    private void evictStaleEntries(Instant now) {
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);
        Iterator<Map.Entry<String, Deque<Instant>>> it = requestTimestamps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Instant>> entry = it.next();
            Deque<Instant> ts = entry.getValue();
            while (!ts.isEmpty() && ts.peekFirst().isBefore(windowStart)) {
                ts.pollFirst();
            }
            if (ts.isEmpty()) {
                it.remove();
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For for proxied requests (Cloud Run, nginx)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take the first IP (original client)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
