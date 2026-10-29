# Pomodoro

A simple pomodoro timer for Android that silences your notifications while you focus.

- Start a focus session with one tap. Notifications from every app are held back until it ends.
- Pick how long to focus and rest, and tag each session (work, study, gym...).
- Calls, alarms and navigation always get through, and you choose which other apps are never silenced.
- Keeps a log of your sessions, with stats and a CSV export.
- Optional Wear OS watch app: see the timer on your wrist and start or stop sessions from it.

Everything stays on your devices: the app has no internet access and no accounts.

## Screenshots

Phone (sample data):

| Home | Focus | Focus complete | Stats | Menu |
|:---:|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/home.png" width="160" alt="Home"> | <img src="docs/screenshots/focus.png" width="160" alt="Focus session"> | <img src="docs/screenshots/done.png" width="160" alt="Focus complete"> | <img src="docs/screenshots/stats.png" width="160" alt="Stats"> | <img src="docs/screenshots/menu.png" width="160" alt="Menu"> |

Watch (Wear OS):

| Ready | Focus | Focus complete |
|:---:|:---:|:---:|
| <img src="docs/screenshots/watch-idle.png" width="130" alt="Watch, ready"> | <img src="docs/screenshots/watch-focus.png" width="130" alt="Watch, focus"> | <img src="docs/screenshots/watch-done.png" width="130" alt="Watch, focus complete"> |

## Install on your phone

You need Android 8.0 or newer. The app isn't on the Play Store, so you install it from a file.

1. Open the [latest release](https://github.com/ESeNonFossiIo/app-pomodoro/releases/latest) on your phone and download
   `pomodoro-<version>-release.apk`.
2. Open the downloaded file and install it. Android will ask you to allow installing apps from your browser
   or file manager ("install unknown apps"). Allow it for that app.
3. Open **Pomodoro** and tap **Grant access** to let it manage notifications. Without this it can't silence
   anything.
   - On Android 13 and newer, if the access setting is greyed out, go to Settings > Apps > Pomodoro, open the
     menu (⋮) and choose **Allow restricted settings**. Then try again.

To check that a download is intact, download `SHA256SUMS` from the same release and run
`sha256sum -c SHA256SUMS` in the folder with the files.

### Updating

Install the new release over the old one. Your sessions and settings are kept.

If you installed version 0.1.0, uninstall it first. Later releases are signed with a different key, so Android
won't update over it. To keep your history, open **Menu > Stats > Export CSV** before uninstalling.

## How to use it

- **Start.** Choose the focus length (5 to 120 minutes) and break length (5 to 30 minutes) with the **−5** and
  **+5** buttons, pick a tag, and tap **Start focus**. To add a tag use **+ New**; to remove one tap **Edit**,
  then the tag.
- **During focus.** The screen shows a ring that fills as time passes, and notifications are held back. They
  come back when the session ends. Tap **Give up** to stop early; a session you give up on doesn't count
  towards your streak.
- **When it ends.** The screen inverts and the timer keeps counting up (`+mm:ss`) until you tap. Choose
  **Start break** or **Done**.
- **Which apps can interrupt.** In **Menu > Apps allowed during focus**, tick the apps whose notifications
  should never be held back. Your phone and messaging apps are allowed by default. You can search the list.
- **Stats.** **Menu > Stats and session log** shows your totals, the last 14 days, time per tag and every
  session. Tap a tag to change it, tap ✕ to delete a session, and **Export CSV** to save your data.
- **Fullscreen and lock.** By default a focus session takes over the screen and tries to keep you in the app
  using Android's *app pinning*. This only works if you turn on **App pinning** in your phone's security
  settings (the app tells you if it's off), and Android shows its own "App is pinned" message each time. You can
  turn it off in the Menu.
- **Look.** The Menu has a colour theme and a black-and-white one. Both follow your phone's light or dark
  mode.

## Install on your watch (optional)

The watch app works with Wear OS 3 or newer (Pixel Watch, Galaxy Watch 4 and later, and others). It needs the
phone app, and both devices need Google Play services. It shows the timer and tag from your phone and has
**Start**, **Give up** and **Break** buttons. If the phone is out of reach, the watch says so and disables the
buttons. The screen stays on while the app is open, so the timer doesn't drop back to the watch face; close
the app to let the watch sleep again. With Always-on display turned on, the timer is shown dimmed instead.

Watch apps can't be installed from a file on the watch itself, so you need a computer with
[adb](https://developer.android.com/tools/releases/platform-tools):

1. Download `pomodoro-wear-<version>-release.apk` from the latest release.
2. Turn on developer mode on the watch:
   - Most Wear OS watches: Settings > System > About > Versions, tap **Build number** 7 times.
   - Galaxy Watch: Settings > About watch > Software, tap **Software version** 5 times.
3. In the watch's **Developer options** turn on **ADB debugging** and **Wireless debugging** (**Debug over
   Wi-Fi** on Samsung). The watch and the computer must be on the same Wi-Fi network.
4. Tap **Pair new device** on the watch. It shows an address, a port and a code. On the computer:

   ```bash
   adb pair <address>:<pairing-port>        # then type the code
   ```

5. Go back to the Wireless debugging screen. It shows a different address and port. Connect and install:

   ```bash
   adb connect <address>:<port>
   adb -s <address>:<port> install pomodoro-wear-<version>-release.apk
   ```

6. Open **Pomodoro** on the watch. Turn Wireless debugging off again afterwards to save battery.

Install the phone and watch apps from the same release. They have to be signed with the same key to talk to each
other.

## Good to know

- Pomodoro holds notifications back, it doesn't block them: a notification can flash for a moment before it is
  snoozed.
- The end-of-session alert can be a few seconds late, because Android saves battery by batching alarms.
- Your sessions and settings are stored only on your devices. If you use the watch, the two devices share the
  timer through Wear OS's built-in data link (part of Google Play services), which Android manages.

## For developers

Building, releasing and how it works: see [docs/development.md](docs/development.md).
