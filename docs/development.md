# Development

Notes for building, releasing and changing Pomodoro. For installing and using the app, see the
[README](../README.md).

## Layout

| Path | What |
|---|---|
| `app/` | Phone app (Kotlin, Jetpack Compose, minSdk 26) |
| `wear/` | Wear OS app (Compose for Wear OS, minSdk 30) |
| `kotlin-devkit/` | Git submodule with the Makefile helpers (`make debug`, `make release`, ...) |
| `docs/` | This file and the README screenshots |
| `.github/workflows/` | `debug.yml` (every push) and `release.yml` (`v*` tags) |

Phone code, in `app/src/main/java/dev/pomo/`:

- `Timer.kt`: the timer state machine (`IDLE`, `FOCUS`, `BREAK`, `DONE`), stored in SharedPreferences so the
  UI, the alarm, the notification listener and the watch all read the same state. `settle()` moves finished
  phases along; a finished focus session becomes `DONE` and counts up until the user taps.
- `BlockerService.kt`: a `NotificationListenerService` that snoozes notifications during focus, except from
  allowed apps, calls, alarms, navigation and ongoing notifications.
- `AlarmReceiver.kt`: fires the end-of-phase alarm (`setAndAllowWhileIdle`, so it isn't exact to the second).
- `History.kt`: SQLite log of every session (finished or given up), stats and CSV export.
- `MainActivity.kt`, `Theme.kt`: the Compose UI and the colour and black-and-white themes.
- `AppCatalog.kt`: installed apps and icons, loaded in the background and cached.
- `WearSync.kt`: publishes the timer state to a paired watch and handles its commands.

## Build

Needs JDK 17, the Android SDK (platform 35, build-tools 35) and Gradle 8.9.

```bash
gradle assembleDebug          # phone and watch debug APKs
make debug                    # same, through the devkit
make phone                    # build, install on the connected phone, launch
```

APKs are named `pomodoro-<versionName>-<variant>.apk` (phone) and `pomodoro-wear-<versionName>-<variant>.apk`
(watch). The watch module reads its version from `app/build.gradle.kts`, so `make bump` covers both.

Machine-specific settings (`JAVA_HOME`, `ANDROID_HOME`, signing keys, the watch address) go in a git-ignored
`env.mk` at the project root. See `kotlin-devkit/env.mk.example`.

With a phone and a watch attached, `adb` needs to be told which device to use:

```bash
make phone ADB="adb -s <phone-serial>"
make wear-install WATCH=<ip>:<port>
```

Other devkit targets: `make logcat`, `make logcat-save`, `make run`, `make clean`, `make help`.

## Wear OS

The phone owns the timer. It publishes its state as a Wearable Data Layer item (`/pomo/state`: phase, end time,
total length, category, focus and break minutes) every time it changes. The watch sends `/pomo/start`,
`/pomo/stop`, `/pomo/break` and `/pomo/sync` messages back, and the phone applies them even when its UI isn't
open. The phone declares a `pomodoro_phone` capability so the watch can tell whether the phone app is installed
and reachable. Both apps use the application id `dev.pomo` and must be signed with the same key.

Install on a watch over wireless debugging (no store needed):

1. Enable Developer options. On Wear OS: Settings > System > About > Versions, tap Build number 7 times. On a
   Galaxy Watch: Settings > About watch > Software, tap Software version 5 times.
2. In Developer options turn on ADB debugging and Wireless debugging (Debug over Wi-Fi on Samsung). The watch
   and the computer must be on the same Wi-Fi.
3. Pair and connect. The pairing screen shows its own IP and port; the main screen shows a different port:

   ```bash
   adb pair <ip>:<pairing-port>       # enter the code shown on the watch
   adb connect <ip>:<connect-port>
   make wear-install WATCH=<ip>:<connect-port>
   ```

## Signing and releases

Release builds are signed with a dedicated key that is kept outside the repository. The signing config in
`app/build.gradle.kts` and `wear/build.gradle.kts` reads its settings from environment variables, which
`env.mk` exports for local builds:

```make
export KEYSTORE            = <path to the keystore>
export KEYSTORE_PASSWORD   = <password>
export KEY_PASSWORD        = <password>
export KEY_ALIAS           = release
export RELEASE_CERT_SHA256 = <SHA-256 of the release certificate>
```

`RELEASE_CERT_SHA256` makes `make release` and `make publish` refuse any APK signed with another key. Get it
with `keytool -list -v -keystore <keystore>`.

**Back up the keystore and its password.** Without them an installed app can't be updated in place: users would
have to uninstall it, which erases its data.

To release:

```bash
make bump PART=patch      # or minor / major / X.Y.Z: versionName and versionCode
# commit and push, then either:
make publish              # build locally, verify, upload to a GitHub release
git tag vX.Y.Z && git push origin vX.Y.Z   # or let GitHub build and publish it
```

Use one or the other. Both build the phone and watch APKs, check that each APK's embedded version matches
`versionName` and `versionCode` and that it is signed with the release key, and attach both plus a `SHA256SUMS`
file. `make publish` also refuses to run with uncommitted or unpushed changes. If a release for the tag already
exists, the workflow does nothing.

### GitHub setup

The release workflow needs these on the repository:

- Secrets: `KEYSTORE_BASE64` (the keystore, base64), `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.
- Variable: `RELEASE_CERT_SHA256`.

```bash
base64 -w0 <keystore> | gh secret set KEYSTORE_BASE64
printf '<password>' | gh secret set KEYSTORE_PASSWORD
printf '<password>' | gh secret set KEY_PASSWORD
printf '<alias>' | gh secret set KEY_ALIAS
gh variable set RELEASE_CERT_SHA256 --body '<sha256>'
```

The `debug` workflow builds debug APKs on every branch push and uploads them as the `pomodoro-debug`
artifact. Those are signed with a throwaway key, so they can't be installed over a release.

## README screenshots

Taken with `adb screencap` on a phone and a watch with sample data, with the status bar cropped out so no
notification icons from other apps show. Keep them small (the phone shots are 360 px wide).

## Known limits

- A notification can flash for a moment before it is snoozed.
- The end-of-session alarm is not exact to the second.
- Locking the phone during focus uses Android's app pinning. The system shows its own "App is pinned" prompt,
  which an app can't suppress, and on some phones leaving pinned mode locks the device.
