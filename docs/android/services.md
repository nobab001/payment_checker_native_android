# Android Services & Background Work

## Categories

| Type | Examples | When |
|------|----------|------|
| Broadcast / receiver | SMS receive path | Incoming SMS events |
| Foreground service | Keep-alive style services | Continuous operator mode |
| Alarm / receiver | `KeepAliveAlarmReceiver` | Schedule wake/keepalive |
| WorkManager | `SmsServiceWatchWorker` | Watchdog / deferred checks |
| Engines | `NumberHeartbeatEngine` | Periodic presence/heartbeat to API |

## Design rules

1. **Reuse** existing service/worker classes; extend carefully.
2. Foreground services must show a compliant ongoing notification.
3. Heartbeat endpoints must tolerate intermittent connectivity; backoff rather than tight spam loops.
4. Persist only necessary state; use encrypted storage for secrets (`SecurePreferences`, HMAC secret keys).
5. On logout / deauth, stop or idle background work that requires a session.

## SMS path (conceptual)

Device SMS → local parse/template match → API ingest → session match → merchant callback.

Template names / provider tags should remain consistent with `sms_templates` on the server.

## Battery & OEM

Document vendor-specific battery exemptions in operator onboarding when needed. Avoid starting redundant overlapping workers.

## Change safety

Background changes can brick operator reliability. Prefer feature flags / gradual rollout and always test on a physical device before VPS-wide APK publish.
