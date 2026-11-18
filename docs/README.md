# Project documentation

Small, living documents. They exist so that humans **and AI agents** make consistent decisions.
A doc that is out of date is worse than no doc - update it in the same change as the code.

| # | Doc | Answers | Owner |
|---|---|---|---|
| 00 | [Kickoff questionnaire](00-kickoff.md) | The raw answers that seed everything else | Product owner |
| 01 | [Vision](01-vision.md) | Problem, users, principles, non-goals, success criteria | Product owner |
| 02 | [Requirements](02-requirements.md) | What it must do, quality targets, constraints (traceable IDs) | Product owner + lead dev |
| 03 | [Product design](03-product-design.md) | Roles, use cases, domain model, screens, API resources | Product owner + lead dev |
| 04 | [Architecture](04-architecture.md) | Code structure, data, security, API, testing, deployment | Lead dev |
| 05 | [Roadmap](05-roadmap.md) | Milestones, exit criteria, now/next/later | Product owner |
| -- | [ADRs](adr/) | Individual decisions and their reasons | Whoever decides |

## Status lifecycle

Every doc starts with a `Status:` line:

- `TEMPLATE` - untouched template; agents must not treat its content as project truth.
- `DRAFT` - filled in, still being validated (walking skeleton / MVP phase).
- `AGREED` - stable; change it deliberately and note why in the change history at the bottom.

## Conventions

- Requirement IDs: `FR-001` functional, `NFR-001` non-functional, `UC-01` use cases. Never reuse an ID;
  mark dropped ones `Dropped`.
- Priorities: MoSCoW (`Must`, `Should`, `Could`, `Won't (this time)`).
- Reference IDs in commits, tests and ADRs (`FR-012: password reset`), so intent stays traceable.
- Keep each doc under ~2 screens; split into sub-pages only when it really grows.
