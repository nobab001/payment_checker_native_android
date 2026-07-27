# Color System

## Brand & UI tokens

Defined primarily in `app/.../ui/theme/Color.kt` and screen-local premium tokens (e.g. Login).

### Core brand

| Name | Hex | Role |
|------|-----|------|
| RoyalIndigo | `#1A237E` | Primary brand (Material theme light) |
| RoyalIndigoLight | `#3949AB` | Primary container / dark primary |
| LoginPrimary | `#1D45D9` | Premium CTA / auth accent |
| LoginPrimaryLight | `#2D5BFF` | CTA gradient end |

### Neutrals & text

| Name | Hex | Role |
|------|-----|------|
| AppBackground | `#F5F7FA` | Light app wash |
| Login bg top/bottom | `#F7F9FC` → `#EEF4FF` | Auth gradient light |
| CardBackground | `#FFFFFF` | Light surface |
| TextPrimary | `#212121` / `#0F172A` | Titles |
| TextSecondary | `#757575` / `#687280` | Captions |

### Dark theme

| Name | Hex | Role |
|------|-----|------|
| Background | `#0B0E14` / `#0F172A` | Page |
| Surface | `#131722` / `#1A2336` | Cards |
| On-surface | `#F5F7FA` | Titles |
| On-surface variant | `#9095A1` / `#B0B8C4` | Secondary text |
| Auth gradient | `#0F172A` → `#1E295B` | Login background |

Theme wiring: `Theme.kt` (`DarkColorScheme` / `LightColorScheme`) + preference `pcu_app_theme` = `light` | `dark` | `system` (default **system**).

### Status

| Name | Hex |
|------|-----|
| StatusGreen | `#4CAF50` |
| StatusOrange | `#FF9800` |
| StatusRed | `#F44336` |
| SoldOutRed | `#FFEBEE` (tint surface) |

### Operator (gateway only)

| Operator | Hex |
|----------|-----|
| bKash | `#E2136E` |
| Nagad | `#EF4123` |
| Rocket | `#6A2C91` |
| Upay | `#00B99B` |

### Social

| Brand | Hex |
|-------|-----|
| WhatsApp | `#25D366` |
| Facebook | `#1877F2` |
| Telegram | `#2CA5E0` |
| YouTube | `#FF0000` |

## Contrast

- Body text on surfaces must meet readable contrast (prefer WCAG AA for UI text).
- Do not use primary blue text on primary blue fills.
- Error banners: tinted surface + StatusRed text/icon.

## Adding a color

1. Add named `val` in `Color.kt` (or documented screen palette).
2. Update this file.
3. Prefer mapping into `MaterialTheme.colorScheme` when broadly reused.
