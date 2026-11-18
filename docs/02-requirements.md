# Requirements - fotozielnik.pl

Status: TEMPLATE

IDs are permanent. Status values: `Proposed`, `Planned`, `In progress`, `Done`, `Dropped`.
Acceptance criteria must be checkable - ideally by an automated test (name the test).

## Functional requirements

| ID | Requirement | Priority | Milestone | Status | Acceptance criteria / test |
|---|---|---|---|---|---|
| FR-001 | _e.g. A visitor can register with email and password_ | Must | M1 | Proposed | _Account created, confirmation email sent; `RegistrationIT`, `e2e/register.spec.ts`_ |

## Non-functional requirements

| ID | Category | Requirement | Target / budget | Status |
|---|---|---|---|---|
| NFR-001 | Performance | API latency | _e.g. p95 < 300 ms for reads at expected load_ | Proposed |
| NFR-002 | Performance | Frontend load | _e.g. LCP < 2.5 s on 4G mid-range phone; JS bundle < 300 kB gz_ | Proposed |
| NFR-003 | Capacity | Users / data | _e.g. 1k users, 50 concurrent, 1M rows in largest table_ | Proposed |
| NFR-004 | Availability | Uptime, maintenance | _e.g. 99.5 %/month; zero-downtime deploys not required_ | Proposed |
| NFR-005 | Security | Authentication, authorization | _e.g. OWASP ASVS L1; deny-by-default; passwords bcrypt/argon2_ | Proposed |
| NFR-006 | Privacy | Personal data | _e.g. GDPR: export + deletion on request; data in EU_ | Proposed |
| NFR-007 | Compatibility | Browsers, devices | _e.g. last 2 versions of Chrome/Firefox/Safari/Edge; >= 360 px wide_ | Proposed |
| NFR-008 | Accessibility | Standard | _e.g. WCAG 2.2 AA for all user-facing screens_ | Proposed |
| NFR-009 | Localization | Languages | _e.g. EN at launch, all UI text externalised_ | Proposed |
| NFR-010 | Operability | Logs, metrics, backups | _e.g. structured logs, /actuator/health, daily DB backups kept 14 days_ | Proposed |
| NFR-011 | Quality | Automated tests | _Unit/slice tests for all logic; *IT per feature; e2e per critical flow_ | Planned |
| NFR-012 | Legal | Licences, policies | _Dependency licences compatible; privacy policy and cookie notice if needed_ | Proposed |

## Constraints
- Team / time:
- Budget:
- Tech: Java 25, Spring Boot 4, PostgreSQL, React + TypeScript (template baseline, ADR-0001).
- Other:

## Assumptions and risks
| # | Assumption or risk | Impact | Mitigation |
|---|---|---|---|
| R1 | _Scope creep_ | High | Non-goals in 01-vision; milestone exit criteria in 05-roadmap |

## Change history
| Date | Change |
|---|---|
