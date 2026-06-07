# Phase 2.5 — LLM Integration, Rate Limiting & Abuse Prevention

## Overview

Replaces the placeholder LLM response with real Gemini streaming, adds multi-layer rate limiting, usage tracking, circuit breaker, abuse detection, and audit logging.

## Components

### GeminiChatService
Wraps LangChain4j's `GoogleAiGeminiStreamingChatModel` for real-time streaming:
- Streams tokens via callback as they arrive from Gemini
- Loads last 20 messages from conversation as context
- System prompt with security hardening
- Falls back to placeholder if `GEMINI_API_KEY` not set
- Configurable model, temperature, max output tokens, timeout

### UsageService
Atomic per-user daily usage tracking via `usage_logs` table:
- Increments `query_count`, adds `input_tokens` + `output_tokens` per request
- One row per user per day (upsert pattern)
- Used by `RateLimiterService` for limit enforcement

### RateLimiterService
DB-backed daily limits checked before each LLM call:
- **Daily query limit:** 50 queries/day (configurable)
- **Daily token budget:** 500,000 tokens/day (configurable)
- Returns descriptive error message when exceeded
- Resets at midnight (new `query_date` row)

### Circuit Breaker
Config flag to instantly disable all LLM calls:
- `circuit-breaker.enabled: true` → all chat requests get 503
- No tokens consumed, no usage counted
- Useful for cost incidents or API outages

### IpRateLimitFilter
Servlet filter for per-IP rate limiting:
- 100 requests/hour per IP (configurable)
- In-memory sliding window (ConcurrentHashMap)
- Runs before JWT authentication
- Only applies to `/api/` endpoints
- Supports `X-Forwarded-For` for proxied requests

### AbuseDetectionService
In-memory pattern detection:
- **Rapid-fire:** >5 queries/minute → 60s throttle
- **Identical messages:** Same message 3+ times in 5 minutes → throttle
- Auto-expires throttle after window passes

### AuditService + audit_logs table
Immutable append-only audit trail:
- Logs every chat request with userId
- Logs tool calls with name, arguments, result
- Logs auth actions (login, consent, revoke) with IP
- RLS-enabled for data isolation

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GEMINI_API_KEY` | Yes (prod) | — | Google AI Gemini API key |
| `GEMINI_MODEL` | No | `gemini-2.0-flash` | Gemini model name |
| `rate-limit.daily-query-limit` | No | `50` | Max queries per user per day |
| `rate-limit.daily-token-budget` | No | `500000` | Max tokens per user per day |
| `rate-limit.ip-requests-per-hour` | No | `100` | Max requests per IP per hour |
| `circuit-breaker.enabled` | No | `false` | Kill switch for LLM calls |

## Request Flow

```
Request → IP Rate Limit Filter → JWT Auth → Security Context
  → ChatService:
    1. Circuit breaker check
    2. Input sanitization
    3. Rate limit check (daily queries + token budget)
    4. Abuse pattern check (rapid-fire + duplicates)
    5. Save user message to DB
    6. Load conversation history (last 20 msgs)
    7. Stream from Gemini → SSE tokens to client
    8. Save AI response to DB
    9. Record usage (query count + tokens)
    10. Audit log entry
    11. Send 'done' SSE event
```

## Database Changes

- New table: `audit_logs` (V8 migration)
- RLS enabled on `audit_logs`

## SSE Event Format (Updated)

```
event: done
data: {"messageId": "uuid", "inputTokens": 150, "outputTokens": 245}

event: error
data: {"error": "Daily query limit reached (50 queries). Resets at midnight UTC."}
```
