---
name: verify
description: Full verification pass for Spring + React changes - static check, backend unit/integration tests, frontend tests, API type sync, e2e tests and a screenshot review, rule review and a manual-check list for the user. Use before declaring a feature or fix done.
---

# Verify

Check the session-start note for what is available (Docker, node_modules, Playwright browser, dev stack).

1. **Static check.** `tools/check.sh` - compile with Error Prone/NullAway, Spotless, TS typecheck, oxlint.
   Fix every error; fix warnings in code you touched. (`--fix` applies Java formatting.)
2. **API contract.** If a controller or DTO changed: `tools/dev.sh up`, then `cd frontend && npm run api:types`
   and run `tools/check.sh frontend` - a type error there means the frontend relies on the old shape.
   Commit the regenerated `src/api/schema.gen.ts` with the backend change.
3. **Tests.** `tools/test.sh` (backend incl. `*IT` with Testcontainers, frontend Vitest). Without Docker,
   `tools/test.sh --unit` and say that integration tests did not run. New logic without tests is not done.
4. **End to end.** `tools/test.sh e2e` for user-visible flows. New screens/flows get a Playwright test in
   `frontend/e2e/`.
5. **Look at it** (any visible change): `tools/look.sh <path> --name <what>` (add `--mobile` for layout work),
   then Read `captures/<what>.png`. Describe what you see and whether it matches the intent. Console errors
   and HTTP >= 400 responses it reports are failures unless expected (then say why).
6. **Repo lint:** `tools/lint-repo.sh --base origin/main` (or plain `tools/lint-repo.sh` on main) - immutable
   migrations across the whole branch, secrets, generated files.
7. **Rules review** of the diff (`git diff` plus untracked files) against `AGENTS.md` "Hard rules":
   no edited applied migrations, entities not exposed by controllers, new endpoints deliberately placed in
   `SecurityConfig`, validation on request DTOs, transactions in services, no secrets, no N+1 in loops.
8. **Docs:** requirement statuses in `docs/02-requirements.md` updated; ADR added for decisions; roadmap "Now" current.
9. **Report** to the user:
   - What was verified automatically (commands + results, capture paths).
   - **Manual checks** still needed (exact steps: open URL, log in as X, do Y, expect Z) - real-browser
     feel, copy, accessibility with a screen reader, behaviour against real external services.
   - Anything not verified and why (e.g. Docker not available).
