# ADR-0012: Use React Router, TanStack Query, i18next and plain CSS for the frontend

- Status: Accepted
- Date: 2026-11-18
- Related: NFR-008, NFR-009

## Context
Older users need large, clear UI (WCAG 2.2 AA, >= 18 px base). Polish first, i18n-ready. Data-heavy views
(grids, progress polling, map).

## Decision
React Router (routes), TanStack Query (server state, polling, infinite grids), i18next + react-i18next
(`src/i18n/pl.json`), `hash-wasm` in a Web Worker (streaming SHA-256 of large files), MapLibre GL in M4.
Styling with plain CSS + design tokens and own accessible components; no UI kit.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| UI kit (MUI, Mantine) | Many components | Dense defaults, override fight for large type; bundle size |
| Leaflet instead of MapLibre | Simpler | Weaker with many points; raster only |
| Web Crypto for hashing | No dependency | No streaming: whole file in memory |

## Consequences
- More own components (grid, dialog), but full control over size and contrast.
