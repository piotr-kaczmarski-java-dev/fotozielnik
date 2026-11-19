# ADR-0005: Use session-cookie login with admin-created accounts

- Status: Accepted
- Date: 2026-11-18
- Related: FR-001, FR-002, FR-003, NFR-005

## Context
Users are a handful of older, non-technical people; the owner wants admin-created accounts (decided).
The SPA is served from the same origin as the API. There is no email service in the MVP. Image endpoints
(`/api/photos/{id}/thumb`) are loaded by `<img>` tags, which cannot send an `Authorization` header.

## Decision
- Email + password; passwords hashed with Spring's `DelegatingPasswordEncoder` (bcrypt default).
- Server-side session in an `HttpOnly`, `Secure`, `SameSite=Lax` cookie; sessions stored with
  Spring Session JDBC so deploys do not log everyone out. Idle timeout 14 days (older users should rarely
  have to sign in again).
- JSON login endpoint `POST /api/auth/login` (no form-login redirects; 401 stays for unauthenticated API calls).
- CSRF protection **on**: `CookieCsrfTokenRepository` (readable `XSRF-TOKEN` cookie), the frontend client
  sends `X-XSRF-TOKEN` on unsafe methods.
- No self-registration. The first admin is created on startup from `ADMIN_EMAIL` /
  `ADMIN_INITIAL_PASSWORD` if no admin exists. Admins create users with a temporary password that must be
  changed at first sign-in. Password reset is done by an admin (no email).
- Simple login throttling per email/IP.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| JWT in browser storage + `Authorization` header | Stateless; no CSRF handling | `<img>` cannot send the header: token in URLs (leaks via logs/history/Referer), blob-fetching every thumbnail (no browser cache, memory-heavy) or public image URLs; any XSS can read the token, and our tokens would live for weeks; deactivation/password reset cannot revoke it without refresh tokens + a blocklist (server state again); its benefits (many services, non-browser clients, cross-domain) do not apply to one same-origin server |
| JWT in an `HttpOnly` cookie | Fixes `<img>` and XSS theft | Still needs CSRF; still no revocation - a session with extra steps |
| OIDC (Keycloak) | Standard, SSO, later multi-tenant | Another service to host; overkill for 10 users; harder for "trivial hosting" |
| Magic-link email | No passwords | Needs SMTP and email skills from older users |

## Consequences
- `SecurityConfig` changes from stateless/CSRF-off to sessions + CSRF; `api/client.ts` must send the CSRF header.
- New dependency: `spring-session-jdbc` (+ its tables via a Flyway migration).
- Non-browser clients later (mobile app, sync tools): add personal access tokens/OAuth next to sessions.
- Later hosted product: add self-registration and email reset without changing the session model.
