# Contract — Frontend Login & Session UX

The login page and post-login routing shell (React + react-router + openapi-fetch, served by nginx).

## Login page (`/login`)
- Fields: **company/client**, username, password. (FR-001/019)
- **States**: `idle` → `submitting` (disabled inputs + spinner) → `success` (redirect to the app) or `error`
  (clear, non-revealing message; inputs re-enabled). (FR-001)
- On success: the session lives in an **httpOnly, Secure cookie** (set by the edge/identity) — not accessible to
  page scripts; the app simply redirects to the intended/app route.
- On 401/423: show a generic "invalid credentials" / "account locked" message — never reveal which field failed.

## Session handling
- The session cookie is sent automatically with every `/auth` and `/api` call; it **survives page refresh** and
  is **encrypted at rest** (JWE). No token is kept in JS-readable storage. (FR-021)
- **Protected routes**: an unauthenticated user hitting an app route is redirected to `/login`.
- **Expiry (90-min lifetime) / 401 from edge**: clear the session and redirect to `/login` with a "session
  expired" notice; the user re-authenticates. (FR-021)
- **Sign-out**: call `/auth/logout` (clears the cookie), return to `/login`. (FR-007)
- **Client backend unavailable (503)**: show a "temporarily unavailable" state, not a crash. (FR-016)

## Security (SC-007, FR-010)
- The password is only ever sent in the login request body over the proxied channel; never stored, never logged,
  never placed in the URL.

## Acceptance
- Valid login → lands in the app against the user's own client backend. (SC-001)
- Invalid login → generic error, no redirect. Sign-out → back to `/login`, session no longer works.
- Expired session mid-use → redirected to `/login`. (FR-008)
