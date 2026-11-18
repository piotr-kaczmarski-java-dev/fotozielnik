# Kickoff questionnaire

Status: TEMPLATE

Answer these once at project start (Claude Code: run `/kickoff` to be interviewed).
Short answers are fine; "don't know yet" is a valid answer - it becomes an open question.
The answers are then distilled into docs 01-05.

## 1. Vision
1. One-sentence pitch ("It's a ___ that helps ___ to ___").
2. What problem does it solve, for whom? How do they solve it today?
3. Three product principles (the tie-breakers for every decision, e.g. "fast over flexible").
4. Why would someone choose this over the alternative (including "do nothing")?
5. Explicit non-goals (what this app will deliberately NOT do).
6. How will we know it worked (users, usage, revenue, a date)?

## 2. Users and domain
7. User types / roles and what each may do (anonymous visitor, user, admin...).
8. The 3-5 most important things users do (use cases), in priority order.
9. The core domain objects and how they relate (e.g. "a Team has Projects; a Project has Tasks").
10. External systems to integrate (payments, email, maps, third-party APIs, an existing database).
11. Notifications: email, push, in-app? Triggered by what?
12. Anything real-time (live updates, chat) or offline?

## 3. Quality targets
13. Expected users at launch and in a year; concurrent users at peak; data volume.
14. Response-time expectations (e.g. p95 API < 300 ms) and acceptable downtime.
15. Browsers and devices (desktop only? mobile web? minimum widths?).
16. Accessibility commitment (e.g. WCAG 2.2 AA) and languages at launch.

## 4. Security and data
17. How do users sign in (email + password, magic link, Google/GitHub, company SSO/OIDC, none)?
18. Personal or sensitive data stored? Which jurisdictions (GDPR...)? Retention and deletion rules.
19. Audit needs (who changed what, when)? Data export/import?
20. Payments or file uploads?

## 5. Scope and constraints
21. Team: who, which skills, how many hours per week?
22. Goal: learning project / portfolio piece / side business / commercial product / client work?
23. Deadlines: walking skeleton, MVP, launch.
24. Monthly budget for hosting and services.
25. Business model (free, subscription, one-off, internal tool).

## 6. Tech
26. Hosting and deployment (a VPS with Docker, a PaaS such as Fly.io/Render/Railway, a cloud provider, Kubernetes)?
27. Serve the frontend from the Spring jar (one deployable) or as separate static hosting/CDN?
28. Extra infrastructure needed now (Redis, message queue, object storage, full-text search)?
29. Frontend libraries already preferred (router, data fetching, UI kit/CSS approach, forms)?
30. Background jobs or scheduled tasks?
31. Observability (logs, metrics, error tracking) and backups.
32. Any AI/LLM features? If yes: provider, data-privacy limits, cost ceiling.

## Answers

> Filled in by `/kickoff` or by hand. Keep the question numbers.

## Open questions

-
