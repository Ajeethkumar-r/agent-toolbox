# Phase 2.2 — Authentication & Authorization

## Overview

Google OAuth sign-in with JWT-based session management. Users sign in with their Google account, receive a JWT access token (15 min) and refresh token (7 days). Every authenticated API request sets the PostgreSQL RLS context for data isolation.

## Auth Flow

```
User clicks "Sign in with Google"
    → Frontend gets Google ID token
    → POST /api/v1/auth/google {idToken}
    → Backend verifies with Google
    → Find or create user in DB
    → Return JWT access + refresh tokens
    → Frontend stores access token in memory, refresh token in cookie
```

## API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/auth/google` | Google sign-in → JWT | Public |
| POST | `/api/v1/auth/refresh` | Refresh access token | Public (uses refresh token) |
| POST | `/api/v1/auth/logout` | Invalidate session | JWT |
| GET | `/api/v1/users/me` | Get current user profile | JWT |
| DELETE | `/api/v1/users/me` | Delete account (cascade) | JWT |

## JWT Token Structure

**Access Token (15 min):**
```json
{
  "sub": "user-uuid",
  "email": "user@gmail.com",
  "name": "User Name",
  "iat": 1719000000,
  "exp": 1719000900
}
```

**Refresh Token (7 days):**
```json
{
  "sub": "user-uuid",
  "type": "refresh",
  "iat": 1719000000,
  "exp": 1719604800
}
```

## RLS Context

Every authenticated request automatically sets the PostgreSQL RLS context via an AOP aspect:

```
Request → JWT Filter (extract user_id) → AOP Aspect (SET LOCAL app.current_user_id) → Service → DB (RLS enforced)
```

This means:
- Application code doesn't need `WHERE user_id = ?` — RLS adds it automatically
- Even SQL injection can't leak cross-user data
- RLS is set per-transaction via `set_config('app.current_user_id', userId, true)`

## Security Configuration

- Public: `/health`, `/actuator/**`, `/api/v1/auth/**`
- Authenticated: `/api/v1/**` (everything else)
- Stateless sessions (no server-side session)
- CSRF disabled (stateless API with JWT)

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `JWT_SECRET` | Yes (prod) | HMAC-SHA256 signing key (min 32 chars) |
| `GOOGLE_CLIENT_ID` | Yes | Google OAuth client ID for ID token verification |

## Testing

```bash
# Sign in with Google ID token
curl -X POST http://localhost:8085/api/v1/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken": "google-id-token-here"}'

# Access protected endpoint
curl http://localhost:8085/api/v1/users/me \
  -H "Authorization: Bearer <access-token>"

# Refresh token
curl -X POST http://localhost:8085/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "refresh-token-here"}'

# Delete account
curl -X DELETE http://localhost:8085/api/v1/users/me \
  -H "Authorization: Bearer <access-token>"
```
