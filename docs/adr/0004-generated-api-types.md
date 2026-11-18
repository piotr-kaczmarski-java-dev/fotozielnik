# ADR-0004: Generate the frontend's API types from the backend's OpenAPI spec

- Status: Accepted
- Date: 2026-11-18
- Related: ADR-0001

## Context
Backend and frontend live in one repo and are often changed in the same agent turn. Hand-written
TypeScript interfaces drift from the Java DTOs silently: the code compiles, the page breaks at run time.

## Decision
1. springdoc generates the OpenAPI spec at `/v3/api-docs` (dev only; disabled in the `prod` profile).
2. `config/OpenApiConfig` marks every DTO property `required` unless it is `@Nullable`, matching NullAway's
   non-null default - so generated types are `name: string`, not `name?: string`.
3. `npm run api:types` (backend running) writes `frontend/src/api/schema.gen.ts` with openapi-typescript.
   The generated file is committed with the backend change, never edited by hand (a hook denies it), and
   `src/api/types.ts` gives the generated schemas friendly names.
4. openapi-typescript needs the TypeScript 5 compiler API, which TypeScript 7 (the project's compiler) no
   longer exposes, so the script runs it through `npx` with pinned `openapi-typescript` and `typescript@5`
   in an isolated environment.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| Hand-written TS types | No tooling | Silent drift - the problem we are solving |
| Full client generators (orval, hey-api) | Typed fetch functions/hooks | More generated code; hey-api still needs a TS compiler API at run time |
| Spec generated at build time (springdoc Gradle plugin) | No running backend needed | Boots the app in the build anyway; slower; more moving parts |

## Consequences
- A changed endpoint shape shows up as a TypeScript error in `tools/check.sh frontend` after regeneration.
- Regeneration needs the backend running (`tools/dev.sh up`). When TS-7-compatible generators exist,
  revisit the `npx` workaround.
