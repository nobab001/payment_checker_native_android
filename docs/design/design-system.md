# PayChek Design System

## Intent

PayChek UI should feel calm, precise, and premium — closer to Stripe Dashboard, Linear, and Arc than to dense admin templates. Screens must be scannable in under three seconds: brand → purpose → primary action.

## Principles

1. **Clarity over chrome** — remove decorative boxes that do not aid interaction.
2. **One primary action** per view region.
3. **Consistent rhythm** — only the spacing scale below.
4. **Theme honesty** — Light / Dark / System with full contrast pairs.
5. **Motion with meaning** — short enter/press feedback; no endless loops.

## Foundations (summary)

| Foundation | Spec |
|------------|------|
| Spacing | 4, 8, 12, 16, 20, 24, 28, 32, 40, 48 dp |
| Type | 11–28 sp Material-aligned scale |
| Radius | 12 / 16 / 20 / 24 / 28 / full |
| Elevation | 0 (flat), 2 (chips), 4–8 (cards), soft colored shadows for CTAs |
| Control heights | Input 56–60dp · Button 48–56dp |
| Content width | ~90% width, max 560dp on large layouts |

Details: `color-system.md`, `typography.md`, `component-library.md`, `animation.md`, `brand-guideline.md`.

## Screen patterns

### Auth / marketing-adjacent

Centered column, soft gradient background, logo → title → subtitle → optional pill tag → elevated card (inputs + CTA) → social row.

### Operational dashboards

Top app bar, clear section headers, list/cards with status chips, FAB or primary button only when creating something.

### Settings

Grouped lists, 16dp horizontal padding, destructive actions visually separated.

## States

| State | Treatment |
|-------|-----------|
| Loading | Inline progress in primary button or subtle list shimmer — not full-screen blockers unless first load |
| Empty | Short explanation + one CTA |
| Error | Banner or inline text in danger color; recoverable action |
| Success | Brief confirmation; prefer toast/snackbar over modal |

## Premium SaaS layout checklist

- [ ] Clear top-level title (not buried)
- [ ] Single primary CTA visually dominant
- [ ] Secondary actions as text/outlined only
- [ ] Consistent horizontal page padding (16–24dp)
- [ ] Cards used for interaction clusters only
- [ ] Status chips for state, not paragraphs of color
- [ ] Dark mode parity verified
- [ ] Empty / loading / error / success designed (not blank white)

## Grid & responsive

- Base grid: **4dp**
- Phone: full-bleed content with side padding
- Large phone / fold / tablet: constrain forms to **max 560dp**, centered
- Short viewports: reduce top spacer via `BoxWithConstraints` (auth reference)

## Elevation & blur

- Prefer Material elevation / soft shadow over heavy multi-layer drop shadows
- Colored CTA shadows use primary at low alpha
- Backdrop blur is optional and must not hurt performance on low-end devices

## Anti-patterns

- Rainbow accents unrelated to brand/operators
- Nested cards inside cards
- Uneven gaps (13dp, 17dp)
- Multiple competing CTAs of equal weight
- Light-only hardcoded colors on screens that must support dark theme
- Bootstrap-like dense tables without hierarchy
- Old Material (heavy FAB + cluttered app bars) without purpose
