# Android Accessibility

## Product context

PayChek may use Accessibility Service capabilities for assisted capture flows (including messaging UI hierarchies). These paths are security- and stability-sensitive.

## Rules

1. Prefer the **existing** accessibility implementation patterns in the repo; do not invent brittle scrapers.
2. For WhatsApp (or similar) UI hierarchy tasks, follow hardened event handling:
   - Handle relevant click/selection events correctly in multi-select modes
   - Use `rootInActiveWindow` rescans when selection events are suppressed
   - Give clear haptic feedback on successful capture where the product already does
   - Extract numeric amounts with careful regex (e.g. `[0-9.]+`) — avoid over-matching
3. Always provide `contentDescription` for meaningful icons in Compose UI.
4. Maintain minimum touch target sizes (~48dp) for primary controls.
5. Do not convey state by color alone — pair with text/icons.
6. Respect user privacy: capture only what the product requires; never exfiltrate unrelated screen text.

## Permissions UX

Explain why accessibility / SMS / overlay permissions are needed **before** system prompts. Deep-link to settings if denied.

## Testing

Verify on real devices across Android versions used by operators. Emulators are insufficient for SMS + accessibility confidence.
