# Typography

## Family

Default Compose `FontFamily.Default` (platform Roboto on most Android devices). Custom font files are **not** currently shipped under `res/font`. If brand fonts (e.g. Inter/Poppins) are introduced later:

1. Add font resources
2. Wire `FontFamily` in `Type.kt`
3. Update this document

Until then, weight and size create hierarchy — not novelty typefaces.

## Scale

| Role | Size | Weight | Notes |
|------|------|--------|-------|
| Display / Auth title | 28sp | Bold | Product name |
| Title large | 22sp | SemiBold | Section titles (`Type.kt`) |
| Title medium | 18sp | Bold | Dialogs |
| Body large | 16sp | Normal | Inputs, primary body |
| Body | 14sp | Medium/Normal | Secondary copy |
| Label | 12–13sp | Medium | Helper, section labels |
| Caption | 11sp | Medium | Dense metadata |

## Auth screen mapping

- **Payment Checker** — 28sp Bold
- **SMS Payment Verification System** — 16sp Regular, secondary color
- **Secure • Fast • Reliable** — 14sp Medium inside pill
- Button label — 16sp SemiBold
- Social labels — 12sp Medium

## Rules

- Prefer `sp` for text; never mix random half-sizes.
- Truncate with ellipsis on single-line titles rather than wrapping awkwardly on small phones.
- Bangla strings: keep line-height comfortable (≥ 1.35× for multi-line paragraphs).

## Implementation

Canonical Material styles: `app/.../ui/theme/Type.kt` (`Typography`).
