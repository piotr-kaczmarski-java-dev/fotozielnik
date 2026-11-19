# ADR-0011: Use the GBIF backbone as taxonomy, limited to Central European vascular plants

- Status: Accepted
- Date: 2026-11-18
- Related: FR-018, FR-020, FR-021, NFR-012, R4

## Context
Need a taxonomy tree with stable IDs, synonyms and Polish vernacular names, restricted to vascular plants
(decided) occurring in Central Europe.

## Decision
Import a subset of the GBIF Backbone (CC BY 4.0) with a loader command: Tracheophyta, filtered to taxa with
occurrences in PL/DE/CZ/SK/AT/HU (or a Polish/Central European checklist) - accepted species plus synonyms
pointing to them, and their vernacular names (Polish + Latin). Sensitivity (protected/red-listed) comes from
a small curated CSV based on Polish regulations and the red list. Data is versioned; reloads are upserts by
GBIF key, never removing taxa that observations reference.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| World Flora Online | Authoritative plant taxonomy | Weak vernacular names; no occurrence data to scope the list |
| Polish checklist only | Best Polish names | Licence unclear; no neighbouring countries; no stable global IDs |

## Consequences
- Attribution to GBIF in the app. Measure Polish-name coverage; supplement if poor (Q1).
