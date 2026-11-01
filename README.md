# Phone Mode Scheduler (Android)

Switches your phone between **Sound**, **Vibrate** and **Silent** based on the time.

**Built-in rule:** *Night*, **Vibrate from 23:00 to 10:00, every day**. It is added the first time you open the app.

## Features
- Any number of rules. Each has a name, start/end time, mode and days. Rules can run past midnight.
- A switch per rule, plus a master switch.
- A "when no rule is active" mode (Sound by default).
- Overlapping rules: the one that started last wins. A 09:00–09:30 Silent rule inside the night rule works as expected.
- Uses exact alarms at each rule start and end, so there's no background service and battery use is close to zero.
- Survives reboots, app updates, clock changes and time-zone changes.
- If you change the mode yourself, your choice stays until the next rule starts or ends.

## Build and install
Open the folder in **Android Studio** and press Run. Or, with the Android SDK installed:

```bash
./gradlew installDebug        # install on a connected phone
./gradlew testDebugUnitTest   # run the schedule tests
```

Requires Android 8.0+ (API 26). Has no third-party dependencies.

## Permissions (the app asks you on first launch)
| Permission | Why |
|---|---|
| Do Not Disturb access | Needed for **Silent** mode and to turn sound back on reliably. Without it, Silent rules use Vibrate. |
| Alarms & reminders (Android 12+) | Switches at the exact minute. Without it, Android may switch a few minutes late. |
| Run at startup | Sets the alarms again after a reboot. |

On some phones (Xiaomi, Huawei, Samsung "Deep sleep"), also set the app's battery setting to **Unrestricted** so alarms aren't blocked.

## Why not iPhone?
iOS doesn't let apps change the ringer or silent switch. On iPhone, use **Settings → Focus → Sleep → Schedule (23:00–10:00)** or a Shortcuts automation instead.

## Code layout
- `ScheduleEngine.kt`: pure-Kotlin rule logic (active rule, next transition). Unit-tested.
- `ModeScheduler.kt`: sets the current mode and the next alarm.
- `Receivers.kt`: handles the alarm and system events (boot, time change, …).
- `RingerController.kt`: sets the ringer mode through `AudioManager`.
- `RuleStore.kt`: saves rules and settings (SharedPreferences/JSON).
- `MainActivity.kt`: the UI (status, permissions, rule list and editor).

## Download the APK
Every push to `main` builds the app on GitHub Actions and publishes it under **Releases**.
Latest build: `https://github.com/<owner>/PhoneModeScheduler/releases/latest/download/PhoneModeScheduler.apk`

Open the file on your phone and allow "Install unknown apps" for your browser or file manager when Android asks.
The APK is signed with a debug key. That's fine for installing it yourself, but you'd need a release signing key to publish on Google Play.
