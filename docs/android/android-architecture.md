# Android Architecture

## Module

Gradle app module under `app/app` with application id `online.paychek.app`.

## Package map

```
online.paychek.app/
├── MainActivity.kt          # Edge-to-edge, AppTheme, nav host, lock overlays
├── Navigation.kt            # Nav entries
├── NavigationKeys.kt        # Serializable NavKey destinations
├── config/AppConfig.kt      # BASE_URL, pref keys
├── data/
│   ├── remote/api/          # Retrofit services + RetrofitClient
│   ├── remote/dto/          # Request/response models
│   └── repository/          # Repositories (e.g. WebsiteRepository)
├── ui/
│   ├── theme/               # Color, Theme, Type
│   └── screen/              # Feature screens + ViewModels
├── services/
│   ├── sms/                 # SMS receiver / HMAC secret usage
│   ├── sync/                # Heartbeat, watch workers
│   └── foreground/          # Keep-alive / alarms
└── utils/                   # SecurePreferences, DeviceIdHelper, adaptive UI
```

## Pattern

**MVVM + Repository**

- Screen composables collect `uiState`
- ViewModels call repositories / API services
- DTOs mirror backend JSON

## Navigation

Navigation3 with typed `NavKey` objects. Unauthenticated start typically `NavKey.Login`. Adding a screen requires:

1. Key in `NavigationKeys.kt`
2. `entry` in `Navigation.kt`
3. Screen composable under `ui/screen/...`

## Networking

- Retrofit via `RetrofitClient`
- Base URL from `AppConfig.API_BASE_URL`
- Auth token stored encrypted and attached per existing interceptor/client setup

## Theme

`AppTheme` reads `pcu_app_theme` (`light`/`dark`/`system`). Default **system**.

## Feature areas (screens)

Auth (login/OTP/signup/PIN), Home/Dashboard, Devices, API Center (websites, settings, docs), Profile, Billing/subscriptions, Admin dashboard, Sync, custom sender tools.

## Testing mindset

- Prefer manual device verification for SMS/accessibility paths.
- Build debug APK after app changes; install via adb when a device is connected.
