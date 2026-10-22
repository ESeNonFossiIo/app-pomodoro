# Pomodoro

Minimal Android pomodoro timer that silences notifications while you focus.

- 25 min focus, 5 min break, started and stopped with one button.
- During focus, notifications from every app are snoozed and reappear when the session ends.
- Apps you allow ("Apps allowed during focus") are never touched. Calls, alarms,
  navigation and ongoing notifications always pass through.
- Records: completed sessions are stored locally (SQLite). Home shows today, streak,
  total and the last 7 days. Giving up early does not count.

## Install (no store)

1. Push to GitHub. The `debug` workflow produces a `pomodoro-debug` artifact containing `pomodoro-<version>-debug.apk`. For a signed release, push a `v<version>` tag: the `release` workflow publishes `pomodoro-<version>-release.apk` as a GitHub Release.
2. Download it, copy to the phone, and install (allow "install unknown apps").
3. Open the app and tap **Grant access** to enable notification access.
   On Android 13+, sideloaded apps may first need
   Settings > Apps > Pomodoro > ⋮ > **Allow restricted settings**.

Local build needs JDK 17, the Android SDK and Gradle 8.9: `gradle assembleDebug`.

## Limits

- A notification can flash for a moment before it is snoozed.
- The end-of-session alarm is not exact to the second (`setAndAllowWhileIdle`).

## Dev loop

Uses [kotlin-devkit](https://github.com/comichao/kotlin-devkit): `make debug`, `make phone`, `make logcat`, `make bump PART=patch`, `make help`.
