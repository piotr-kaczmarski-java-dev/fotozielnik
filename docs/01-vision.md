# Vision - fotozielnik.pl

Status: DRAFT

## Pitch
fotozielnik.pl is a self-hosted photo library ("Immich for wild plants") that helps amateur botanists turn
huge, unsorted archives of wild-plant photos into identified, browsable observations.

## Problem
Amateur botanists photograph wild plants for years with DSLRs and end up with 10-20 GB and tens of
thousands of photos each, dumped in folders by date. Finding "all my photos of *Pulsatilla patens*" or
"what grew at that meadow in May" means scrolling through folders. General photo managers (Immich,
Google Photos) do not know taxonomy, do not group photos of one plant into an observation, and their
recognition is not made for Central European flora. Manual identification of tens of thousands of photos
is not realistic. The first users are older and not technical, so tools that need scripting or desktop
software setup are not an option.

## Users
| Role | Who they are | What they want from the app |
|---|---|---|
| User | Amateur botanist, often 60+, not technical, Polish-speaking | Upload the whole archive once, get it sorted by species, place and date; correct the machine where it is wrong |
| Admin | The owner (technical) | Create accounts for family/friends, reset passwords, keep the server running |

## Product principles
1. **Simple for older users** - large type and targets, one clear workflow per screen, Polish wording,
   no technical jargon. When in doubt, fewer options.
2. **Automate the bulk, the human decides** - the machine groups and identifies; the user confirms or
   corrects in bulk. User corrections are never overwritten by a later automatic run.
3. **Trivial to host, portable** - one compose file, S3-compatible storage, no external AI services;
   the same code can later run as a hosted product.

## Non-goals
Deliberately NOT in the MVP:
- Sharing, groups, public pages, social features (sensitive-location hiding only matters then).
- Native mobile app; designed phone layouts.
- RAW files (JPEG only; RAW only if users ask).
- Non-vascular plants, fungi, animals.
- iNaturalist/GBIF export, phenology views, life lists.
- Open self-registration, email notifications, payments.
- Photo editing.

## Success criteria
- The owner's father and at least 3 friends have imported their full archives (assumed).
- At least 70 % of observations have the correct species in the top 3 suggestions (assumed, measured on
  confirmed observations).
- Users review identifications on their own, without the owner's help (assumed).

## Change history
| Date | Change |
|---|---|
| 2026-11-18 | First draft from kickoff |
