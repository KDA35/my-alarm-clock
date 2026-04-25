# Audit Report: Ringing screen does not auto-launch over locked screen

**Stage 6.1 (v0.6.1) — investigation and multi-layer fix**

## 1. Symptom

On Pixel 10 XL with GrapheneOS (Android 16, BP4A.260205.001), when an alarm
fires:

- Sound plays
- Vibration works
- A notification is posted in the shade
- **`AlarmRingingActivity` does NOT pop up over the locked screen automatically**
- The activity only opens after the user manually unlocks the device and taps
  the notification

Verified by the user: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
`USE_FULL_SCREEN_INTENT` (Special app access → Full screen notifications),
battery optimization (Unrestricted) and the alarm channel (Importance High,
visible on lock screen) are all granted/configured correctly. The app was
installed via sideload from the GitHub Actions APK (no Play Console
declaration).

The 12-second gap between `Service: Foreground started` and
`RingingUI: Activity created, isLocked=false` confirms the activity only
appeared after the user himself tapped the notification post-unlock.

## 2. Code review against best-practices checklist

I read the four relevant files (`AndroidManifest.xml`, `AlarmService.kt`,
`AlarmRingingActivity.kt`, `AlarmReceiver.kt`) and checked them against the
canonical AOSP DeskClock / Material full-screen-intent setup.

| Item | Status |
| --- | --- |
| Manifest — all required `uses-permission` (FSI, exact alarms, post notifications, wake lock, FGS+specialUse, turn screen on, vibrate, boot completed) | ✅ |
| Manifest — `AlarmRingingActivity` with `showOnLockScreen`, `turnScreenOn`, `singleTask`, `taskAffinity=""`, `excludeFromRecents`, `exported=false` | ✅ |
| Manifest — `AlarmService` `foregroundServiceType="specialUse"` + subtype `<property>` | ✅ |
| Manifest — no `DISABLE_KEYGUARD` or other deprecated permissions | ✅ |
| Notification — `setCategory(CATEGORY_ALARM)` + `setPriority(PRIORITY_MAX)` + `setVisibility(VISIBILITY_PUBLIC)` + `setFullScreenIntent(pi, true)` + `setOngoing(true)` + `setSmallIcon` | ✅ |
| Channel — `IMPORTANCE_HIGH` + `setBypassDnd(true)` + `lockscreenVisibility=PUBLIC` + `setSound(null,null)` (sound played via `Ringtone`) | ✅ |
| FSI `PendingIntent` — `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` | ✅ |
| `startForeground` with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on Android 14+ | ✅ |
| `AlarmRingingActivity.onCreate` — `setShowWhenLocked(true)` + `setTurnScreenOn(true)` + `FLAG_KEEP_SCREEN_ON`, **no** `requestDismissKeyguard`, **no** `FLAG_DISMISS_KEYGUARD` | ✅ |
| `AlarmReceiver` — `goAsync()` + `ContextCompat.startForegroundService` | ✅ |

**No deviations from best practices were found in the existing code.** The
implementation is what AOSP DeskClock and major OEM clock apps do.

## 3. Root cause

The behavior is consistent with **Hypothesis A** from the audit brief:

> The code is correct, but GrapheneOS deliberately throttles FSI for
> sideload apps because the FSI permission has been abused for ad/spam
> overlays.

Direct supporting evidence:

- [GrapheneOS issue #1432](https://github.com/GrapheneOS/os-issue-tracker/issues/1432)
  — stock Clock does not show overlay above secure lock screen
- [GrapheneOS issue #350](https://github.com/GrapheneOS/os-issue-tracker/issues/350)
  — alarm cannot be dismissed from the lock screen without unlocking

Additional context: starting Android 14, `USE_FULL_SCREEN_INTENT` became a
special-access permission. Apps that were not pre-declared as a
calling/clock app via the Play Console may receive degraded FSI behavior on
hardened OS variants, even when the user has manually granted the
permission. Sideload installations have no such declaration.

**Conclusion:** FSI alone is not sufficient on GrapheneOS for sideload-only
distribution. A multi-layer fallback architecture is required.

## 4. Decision: 4-layer defense

Each layer is **independent**: the alarm screen will appear if **any one of
them** succeeds. A degraded layer (e.g. user did not grant overlay
permission) silently no-ops while the rest still try.

```
┌──────────────────────────────────────────────────────────────┐
│ Layer 1 — Full-screen intent (FSI)                           │
│   • Notification with setFullScreenIntent(pi, true)          │
│   • Works on stock Android with the right Play declaration   │
│   • On GrapheneOS sideload: often does nothing                │
├──────────────────────────────────────────────────────────────┤
│ Layer 2 — Direct startActivity from foreground service        │
│   • Requires SYSTEM_ALERT_WINDOW (overlay permission)         │
│   • That permission also unblocks background-activity-start  │
│     restrictions on Android 10+                              │
│   • This is the layer that actually fixes GrapheneOS         │
├──────────────────────────────────────────────────────────────┤
│ Layer 3 — Notification action buttons                         │
│   • Inline "Snooze N min" + "Dismiss" actions                │
│   • Fired through AlarmActionReceiver, bypasses the activity  │
│   • Last-resort: even if no UI ever appears the alarm can be │
│     turned off from the shade                                │
├──────────────────────────────────────────────────────────────┤
│ Layer 4 — PowerManager wake lock                              │
│   • FULL_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP | ON_AFTER_RELEASE │
│   • Held for 60s so the screen turns on regardless of whether │
│     Activity attrs (turnScreenOn) are honored by the OEM     │
└──────────────────────────────────────────────────────────────┘
```

### When does which layer save us?

| Scenario | L1 FSI | L2 Overlay | L3 Actions | L4 Wake |
| --- | --- | --- | --- | --- |
| Stock Pixel, Play install, screen on | ✅ shows UI | ✅ shows UI (idempotent — singleTask) | extra control | n/a |
| Stock Pixel, locked, screen off | ✅ wakes + shows | ✅ shows | extra control | belt-and-suspenders |
| GrapheneOS sideload, locked, no overlay perm | ❌ silenced | ❌ no perm | ✅ user can dismiss from shade | wakes screen |
| GrapheneOS sideload, locked, **overlay perm granted** | ❌ silenced | ✅ shows UI | extra control | wakes screen |
| OEM with aggressive battery (Xiaomi/Huawei) | likely ❌ | ✅ if perm granted | ✅ always | ✅ always |
| Phone in DND | ✅ (`bypassDnd`) | ✅ | ✅ | ✅ |

So the **practical fix for the reported bug** is Layer 2 (overlay) — but
Layers 3 and 4 are added for defense in depth and for future devices where
even Layer 2 might be restricted.

## 5. Implementation

### Files changed
- `AndroidManifest.xml` — add `SYSTEM_ALERT_WINDOW` permission, register
  `AlarmActionReceiver` with two intent filters
- `alarm/AlarmActionReceiver.kt` — **new**, `@AndroidEntryPoint` BroadcastReceiver
  that handles `NOTIF_SNOOZE` and `NOTIF_DISMISS`. Stops the service first
  for instant audio silence, then either snoozes (via `AlarmScheduler`) or
  just lets the dismissal stand
- `alarm/AlarmService.kt`:
  - Wake lock acquired in `startAlarm()` (60s timeout, released in
    `stopSelfCleanly()`/`onDestroy()`)
  - `tryLaunchActivityDirectly()` — Layer 2: checks
    `Settings.canDrawOverlays()` and calls `startActivity(...)` with
    `FLAG_ACTIVITY_NEW_TASK` after the foreground notification is posted
  - `buildNotification()` extended with `addAction(snoozePi)` +
    `addAction(dismissPi)`. Snooze duration uses the alarm's configured
    interval; falls back to 10 min
  - New `AlarmFire` log block — every fire emits a numbered breadcrumb
    per layer with the relevant capability check value
  - `lastFireTimestampElapsed` static published so `AlarmRingingActivity`
    can compute "time from fire to UI"
- `alarm/AlarmRingingActivity.kt` — adds `AlarmFire: Activity actually
  appeared at HH:mm:ss.SSS, time from fire to UI: Nms`
- `ui/onboarding/OnboardingScreen.kt` — adds a 5th page `OverlayPage`
  pointing to `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` for the current
  package. Skip remains available — Layer 2 just no-ops if user declines
- Strings: `notif_action_snooze`, `notif_action_dismiss`,
  `onboarding_overlay_title`, `onboarding_overlay_subtitle` (ru + en)

### Diagnostic log block on every fire

```
I/AlarmFire: === ALARM FIRED === alarmId=3
I/AlarmFire: Layer 4 (Wake): wake lock acquired (60s timeout)
I/AlarmFire: Layer 1 (FSI): canUseFullScreenIntent=true, posted notification with full-screen intent
I/AlarmFire: Layer 2 (Overlay): canDrawOverlays=true, started Activity directly
I/AlarmFire: Layer 3 (Actions): notification actions attached (Snooze/Dismiss)
I/AlarmFire: Activity actually appeared at: 09:05:00.314; time from fire to UI: 235ms
```

If a layer is unavailable, the log line tells you why (e.g. `canDrawOverlays=false, skipped`).

## 6. What the user must do after updating

The new APK is a regular update — no reinstall required. After updating to
v0.6.1, **one new system permission must be granted**:

1. Open the app — onboarding will show a new 5th page: **"Поверх других
   приложений" / "Display over other apps"**. Tap **"Открыть настройки"**
   and toggle the switch ON for MyAlarm.
2. Alternatively, do it manually: **Settings → Apps → MyAlarm → Display
   over other apps → Allow**.

Existing users who already passed onboarding can re-enter it via menu →
"Показать туториал ещё раз" (Show tutorial again).

After the permission is granted, the next alarm should pop up over the
locked screen automatically. The `AlarmFire` log block in the in-app Logs
screen will confirm `Layer 2 (Overlay): canDrawOverlays=true, started
Activity directly`.

## 7. What is *not* changed

- Existing alarm/snooze/postpone/dismiss/group/widget/onboarding logic
  remains intact
- FSI was kept as Layer 1 — never removed
- The overlay permission is **optional**; the app still works (degraded —
  Layer 1 + Layer 3 + Layer 4) without it
- No reinstall is required — schema unchanged, no migration in this stage
- App icon, ringing screen design, list/edit screens unchanged

## 8. Version

`versionCode = 8`, `versionName = "0.6.1"`
