# Component Library

Compose building blocks used across PayChek. Prefer Material 3 primitives styled with design tokens over one-off custom views.

## Surfaces

### App scaffold

`MaterialTheme` + `Surface` in `MainActivity`. Screens fill remaining space via navigation host.

### Card

- Radius: 24–28dp (premium), 12–16dp (dense lists)
- Elevation: 4–8dp light; toned down in dark
- Padding: 16–22dp internal
- Use for grouped interactive content (login form, settings group), not every text block

### Pill / chip

Fully rounded; light primary tint background; medium 12–14sp label (e.g. Secure • Fast • Reliable).

## Inputs

### OutlinedTextField

- Height: 56–60dp
- Radius: 16–20dp
- Leading icon for affordance
- Trailing validation icon (check) when format valid
- Placeholder: secondary color, readable size (14–16sp)

### OTP boxes

Custom digit cells with focus border + blinking cursor helpers (see LoginScreen). Keep cell size adaptive on narrow widths.

## Buttons

| Variant | Use |
|---------|-----|
| Primary gradient / filled | Main CTA (verify, save) |
| Outlined | Secondary / cancel |
| TextButton | Tertiary (resend OTP) |

- Height 48–56dp; radius 16–20dp
- Loading: `CircularProgressIndicator` **inside** the button, disable double-submit (debounce already used on login)

## Icons

- Sizes: 20 / 24 / 28 dp
- Social circles: 56–72dp diameter depending on width
- Content descriptions required when icon-only

## Feedback

- **Floating error banner** — top overlay, auto-dismiss ~3s on login
- **Dialogs** — register prompt, device-bound notice, limit exceeded (keep Bangla copy consistent)
- **Progress** — button-level or list-level; avoid nested spinners

## Lists & rows

Use clear leading icon/avatar, title, supporting text, optional trailing chevron. Divide with subtle outlineVariant, not heavy rules.

## Navigation

Bottom/side navigation and route keys live in `Navigation.kt` / `NavigationKeys.kt`. New screens need a `NavKey` + `entry` registration.

## Reuse policy

Before inventing a new component, search `ui/` for an existing pattern. Extract shared composables only after the second real use.
