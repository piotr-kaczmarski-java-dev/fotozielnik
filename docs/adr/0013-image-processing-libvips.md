# ADR-0013: Generate thumbnails with libvips via Java FFM

- Status: Accepted
- Date: 2026-11-18
- Related: FR-008, NFR-003, R2

## Context
Tens of thousands of 20-40 MP JPEGs per import need thumbnails (400 px) and previews (2048 px) with
correct EXIF orientation, overnight on 4 vCPU. Java ImageIO decodes full-size images slowly and with high memory use.

## Decision
Use libvips through the `vips-ffm` binding (Java 22+ FFM, fits Java 25): shrink-on-load JPEG decoding,
WebP output, auto-rotate. libvips is installed in the app image. Benchmark in M1 against TwelveMonkeys
ImageIO; keep the `DerivativeGenerator` interface so the fallback can replace it.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| ImageIO + TwelveMonkeys | Pure Java, no native lib | Slower, much higher memory at full decode |
| Shell out to `vipsthumbnail` / ImageMagick | Simple | Process per image, error handling via exit codes |

## Consequences
- Native library in the image and on dev machines (tests skip or use the fallback when libvips is missing).

## Amendment 2026-11-19
M1 ships with the **fallback** (`ImageIoDerivativeGenerator`: ImageIO + TwelveMonkeys JPEG), because libvips is
not installed on the dev machine and the app image has no native libraries yet. It decodes with JPEG
subsampling (a 40 MP original is decoded at ~preview size), so memory per worker stays small. Output is JPEG
(q 0.80 thumbnail, 0.85 preview); ImageIO cannot write WebP. The libvips/vips-ffm switch is still planned
after the R2 benchmark on a real archive; it only replaces the `DerivativeGenerator` bean.

First data point (2026-11-19, dev machine, one thread): synthetic noisy 6000x4000 JPEG (9.4 MB), EXIF read +
thumbnail + preview with the fallback: **~380 ms**. NFR-003 needs ~1 photo/s overall, i.e. <= ~2.9 s per photo
with 3 workers on 4 vCPU - about 7x headroom here, less on slower VPS cores. Re-measure on a real archive on
the VPS before deciding whether libvips is needed at all.
