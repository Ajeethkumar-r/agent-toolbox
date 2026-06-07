# Phase 2.4 — Security

## Overview

Security hardening for the API: OAuth token encryption, input/output sanitization, CORS, error handling, and suspended user enforcement.

## Components

### TokenService (AES-256-GCM)
Encrypts OAuth tokens before storing in `user_tokens` table:
- AES-256-GCM authenticated encryption
- Random 12-byte IV per encryption (prepended to ciphertext)
- Key from `TOKEN_ENCRYPTION_KEY` env var (32 bytes)
- Used when storing/retrieving Google Drive OAuth tokens

### InputSanitizer
Scans user messages for prompt injection patterns:
- Strips HTML tags
- Detects patterns: "ignore previous instructions", "system prompt", "reveal your instructions"
- Does NOT block — flags and logs for monitoring
- Will be wired into ChatService

### OutputFilter
Redacts sensitive content from LLM responses:
- API key patterns (`AIza...`, `sk-...`)
- Internal paths (`/Users/...`, `/home/...`)
- Connection strings (`jdbc:postgresql://...`)
- JWT tokens (`eyJ...`)
- Replaces with `[REDACTED]`

### GlobalExceptionHandler
Catches all exceptions and returns clean error responses:
- Never exposes stack traces or internal details to clients
- Logs actual exceptions at ERROR level
- Standard response format: `{"error": "message", "status": 400}`

### CORS Configuration
Allows frontend origins:
- `http://localhost:3000` (Next.js dev)
- `http://localhost:8080` (local frontend)
- Methods: GET, POST, PUT, DELETE, OPTIONS
- Credentials: allowed

### Suspended User Check
JWT filter checks `is_suspended` flag on each request:
- If user is suspended → request denied (403)
- Logs suspension attempts for audit

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `TOKEN_ENCRYPTION_KEY` | Yes (prod) | AES-256 key (exactly 32 chars) |
