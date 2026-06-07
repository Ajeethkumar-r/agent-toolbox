# Phase 2.3 — Chat API

## Overview

REST + SSE API for chat with the LLM agent. Includes conversation management, user settings, and SSE streaming for real-time token-by-token responses.

## API Endpoints

### Chat
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/chat` | Send message, receive SSE stream | JWT |
| POST | `/api/v1/chat/stop` | Stop current generation | JWT |

### Conversations
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/conversations` | List user's conversations | JWT |
| GET | `/api/v1/conversations/{id}` | Get conversation with messages | JWT |
| POST | `/api/v1/conversations` | Create new conversation | JWT |
| DELETE | `/api/v1/conversations/{id}` | Soft-delete conversation | JWT |

### User Settings
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/settings` | Get user settings | JWT |
| PUT | `/api/v1/settings` | Update settings | JWT |

## SSE Stream Format

```
event: token
data: {"content": "Hello"}

event: token
data: {"content": " world"}

event: done
data: {"messageId": "uuid", "tokenCount": 42}
```

On stop:
```
event: stopped
data: {"reason": "User requested stop"}
```

## Chat Request

```json
POST /api/v1/chat
{
  "conversationId": "uuid",
  "message": "What's in my drive?"
}
```

## Conversation Auto-Title

First message in a conversation auto-generates the title (first 50 chars of the user's message).

## Data Model

### Conversation
- id, userId, title, isArchived, createdAt, updatedAt, deletedAt, version

### Message
- id, conversationId, role (user/ai/system), content, tokenCount, toolCalls (JSONB), createdAt, updatedAt, deletedAt

### UserSettings
- id, userId, preferredModel, dailyQueryLimit, preferences (JSONB), createdAt, updatedAt, deletedAt, version

### UsageLog
- id, userId, queryDate, queryCount, inputTokens, outputTokens, modelUsed, createdAt, updatedAt

## Notes

- Chat uses placeholder LLM response for now — real LangChain4j streaming integration will be wired in a future phase
- SSE streaming uses `SseEmitter` with 60s timeout
- Stop generation uses `AtomicBoolean` flag per user
- All service methods use `@Transactional` for RLS context
- Soft delete on conversations and messages (sets `deleted_at`)
