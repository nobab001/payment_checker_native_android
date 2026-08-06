<!-- Synced from .cursor/rules/01-ui-ux.mdc -->

# UI / UX Rules

Philosophy: Apple · Stripe · Linear · Raycast · Notion · Arc · Vercel. Never Bootstrap-like, never crowded, never random spacing/colors/fonts.

## Mandatory pre-read

`docs/design/design-system.md`, `docs/design/brand-guideline.md`, `docs/design/component-library.md`

## Layout

- One clear visual hierarchy per screen (title → primary action → secondary).
- Prefer calm whitespace; use spacing scale only (`4/8/12/16/20/24/28/32/40/48`).
- Cards: elevated surfaces for interactive groups only — not decoration.
- Center forms on auth/onboarding; use max content width (`90%`, max `560dp` on large phones/tablets).
- Responsive: `BoxWithConstraints` / `widthIn` for small-height and tablet layouts.

## Components

- Inputs: outlined, rounded (`16–20dp`), leading icon, clear placeholder typography.
- Primary buttons: large (`48–56dp`), rounded (`16–20dp`), Material ripple, loading inside button.
- Icons: consistent size (`20/24/28`), brand social colors only for social brands.
- Empty / loading / error / success: always intentional states (see design-system).

## Theme

- Support Light, Dark, and System via existing `AppTheme` + `pcu_app_theme`.
- First install defaults to **system**. Do not hardcode light-only colors without dark equivalents.
- Prefer `MaterialTheme.colorScheme` + documented brand tokens; avoid one-off hex in random screens unless adding to the design system.

## Animation

- Purposeful, 200–400ms typical; screen enter sequences ≤ ~600–700ms.
- No infinite decorative loops unless product-approved (e.g. subtle logo is optional; prefer one-shot enter).
- Material 3 only — no heavy animation libraries.

## Accessibility

- Content descriptions on icons that convey meaning.
- Touch targets ≥ 48dp where interactive.
- Do not rely on color alone for status.
