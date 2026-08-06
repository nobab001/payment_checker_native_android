<!-- Synced from .cursor/rules/03-android.mdc -->

# Android Engineering Rules

Read: `docs/android/android-architecture.md`, `docs/android/services.md`, `docs/android/accessibility.md`

## Architecture

- UI: Jetpack Compose screens under `ui/screen/...`
- State: ViewModel + `StateFlow` / `collectAsState`
- Data: Repository → Retrofit API services → DTOs
- Navigation: Navigation3 keys in `NavigationKeys.kt` / `Navigation.kt`
- Config: `AppConfig.kt` for `BASE_URL` / prefs keys — never scatter base URLs

## Patterns

- Keep business logic in ViewModel / repository — screens stay UI + wiring.
- Prefer existing utilities (`DeviceIdHelper`, `SecurePreferences`, adaptive size helpers).
- New screens: match existing package layout; no orphan God-activities.

## Services & background

- SMS / heartbeat / keep-alive: extend existing workers/services (`services/sync`, `services/foreground`, `services/sms`).
- WorkManager for deferred/retryable work; Foreground Service only when required for continuous capture.
- Accessibility / overlay / WhatsApp hierarchy work: follow existing hardened patterns; do not invent fragile node scraping.

## Permissions & lifecycle

- Request permissions at point of need with clear UX rationale.
- Cancel collectors / jobs in ViewModel `onCleared`; avoid leaking Context in companions.

## Performance

- Avoid heavy work on main thread; use coroutines with proper dispatchers.
- Minimize recomposition (stable params, avoid huge lambdas capturing unstable state when avoidable).
- No unnecessary animation libraries.

## Hilt / Room

- Prefer the project’s established DI/storage approach. Do not introduce a second DI framework or parallel DB stack without an architecture decision recorded in `docs/`.
