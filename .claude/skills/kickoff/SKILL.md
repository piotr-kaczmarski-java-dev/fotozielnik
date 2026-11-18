---
name: kickoff
description: Interview the user to define a new web application and fill docs/00-05 (vision, requirements, product design, architecture, roadmap). Use at the start of a project created from this template, or when docs still say "Status: TEMPLATE".
---

# Project kickoff

Goal: turn the user's ideas into the project docs, so every later session (human or agent) works from the same truth.

## Steps

1. Read `docs/README.md`, `docs/00-kickoff.md` and skim docs 01-05 to see what is already filled.
   If `docs/00-kickoff.md` already has answers, resume from the first unanswered section.
2. Interview the user **section by section** (Vision, Users and domain, Quality, Security and data, Scope, Tech):
   - Use the AskUserQuestion tool when a question has sensible discrete options (max 4 questions per call),
     with a recommended option first. Ask open questions in plain text.
   - Offer a concrete default for anything the user is unsure about and mark it `(assumed)`.
   - Push back gently on scope that contradicts their stated team/time constraints - that is the most
     valuable thing a kickoff does. Typical traps: multi-tenancy, microservices, real-time, offline mode,
     "admin panel for everything", SSO for an MVP.
   - After each section, write the answers into `## Answers` in `docs/00-kickoff.md` (keep question numbers)
     so progress survives an interrupted session.
3. Distil the answers into the docs (keep each concise; no filler):
   - `01-vision.md`: problem, pitch, users, 3 product principles, non-goals, success criteria.
   - `02-requirements.md`: initial FR list (Must/Should for M1-M2 only - do not invent a full backlog) and
     concrete NFR targets (latency, availability, security, privacy, browsers); constraints; top 3-5 risks.
   - `03-product-design.md`: roles and permissions, core use cases, domain model (entities + relations),
     screens and flows, API resources for M1, open questions.
   - `04-architecture.md`: review the template baseline; fill feature modules, data model, auth choice,
     deployment at the level known now.
   - `05-roadmap.md`: dates/targets per milestone, and a "Now" list for M1 (walking skeleton).
   - Replace `{{PROJECT_NAME}}` / `{{ONE_LINE_PITCH}}` placeholders in `AGENTS.md` and docs if still present.
4. Set every filled doc to `Status: DRAFT` and add a change-history row with today's date.
5. Record significant decisions as ADRs using the `/adr` skill conventions. **Always** one for the
   authentication approach (it changes `SecurityConfig`, CSRF and the frontend), and one each for e.g.
   hosting/deployment, extra infrastructure (Redis, a queue, object storage) or extra libraries.
6. Finish with a short summary: the pitch, the principles, what M1 must prove, and the open questions -
   then propose the first 3 concrete tasks for the walking skeleton (one thin vertical slice:
   migration -> entity -> service -> controller -> generated types -> screen -> e2e test).

Do not write application code during kickoff.
