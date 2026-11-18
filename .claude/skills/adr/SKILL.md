---
name: adr
description: Record an architecture/technical decision as a numbered ADR in docs/adr/. Use when choosing libraries or infrastructure, authentication, API style, data model or persistence strategy, deployment, module boundaries, version upgrades, or reversing an earlier decision.
---

# Architecture Decision Record

1. List `docs/adr/` and take the next free number (4 digits, e.g. `0007`).
2. Copy the structure of `docs/adr/0000-template.md` into `docs/adr/NNNN-short-kebab-title.md`.
3. Fill it from the conversation: context (forces, constraints), the decision in plain words, at least two
   real alternatives with pros/cons, and consequences including follow-up tasks. Date = today.
   Status `Accepted` if the user decided, otherwise `Proposed` - and ask them to decide.
4. If it replaces an older ADR, set the old one to `Superseded by ADR-NNNN` (only that line changes).
5. If the decision changes the architecture, update `docs/04-architecture.md` and its change history.
6. Reply with the ADR path and a one-line summary.
