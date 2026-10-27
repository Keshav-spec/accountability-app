# Steady — personal accountability

Steady is a calm, single-user planner for Android and Windows. Stage 3 adds optional Supabase accounts and task/check-in sync on top of the local task management, daily planning/check-ins and opt-in local reminders. It works locally without Supabase credentials or a network connection.

## Prerequisites

- Android Studio, JDK 17, Android SDK Platform 36, and Gradle 8.13 (AGP 8.13.2, Kotlin 2.3.21).
- Node.js 20 or newer and npm (web app uses Vite 7).
- Supabase is optional. Without it, the apps continue to work from Room/IndexedDB.

## Run the web app

```sh
cd web
npm install
npm run dev
```

Open the local URL printed by Vite. `npm test` runs the model and reminder-rule tests. `npm run build` makes a production PWA bundle; use `npm run preview` to preview it. Service workers and browser notifications require HTTPS or localhost. Browser reminders run while the app page is open; support varies by browser and OS.

## Run Android

Set `ANDROID_HOME` (or `sdk.dir` in ignored `android/local.properties`) to your Android SDK. With an API 26+ emulator/device connected:

```sh
gradle -p android testDebugUnitTest
gradle -p android installDebug
```

Alternatively, open `android/` in Android Studio, configure Gradle 8.13 and select the `app` run configuration. The Android app stores tasks, check-ins, reminder preferences/history, and opt-in usage selections/aggregates in Room. Android notifications require the notification permission on API 33 and later; the app explains this before opening the system prompt. See [docs/STAGE4_ANDROID_USAGE.md](docs/STAGE4_ANDROID_USAGE.md) for the Usage Access flow and [docs/STAGE5_ANDROID_BLOCKING.md](docs/STAGE5_ANDROID_BLOCKING.md) for optional app limits and device verification.

## Manual test checklist

1. Create a task with a title, notes, date and optional target time. Try a blank title, edit it and move it to a future date.
2. Complete a task, undo it, skip it, and confirm today’s done/skipped list and upcoming list update.
3. Add up to three morning priorities and an intention; save a progress check-in and evening reflection with Complete, Mixed or Rest. Reopen the app and confirm they remain.
4. Configure reminder times, quiet hours and a daily limit. Enable reminders; confirm Android’s permission prompt (API 33+) or the browser’s explicit permission prompt. On Android, wait for a reminder and try Snooze and Stop today. In the browser, keep the app page open until a test reminder time and try the notification actions.
5. Confirm quiet hours suppress or defer a reminder, completed/skipped tasks do not get further reminders, the daily limit is observed, and disabling reminders stops future reminders.
6. Restart each app and confirm local tasks, check-ins and reminder settings remain. Try offline after the first web load.
7. For configured Supabase: create/sign in to the same account on Android and Windows, create a task on Android and confirm it appears on Windows, edit it on Windows and confirm the change appears on Android. Also verify check-ins, offline edits, deletion tombstones, and RLS using a second account. See [docs/STAGE3_SUPABASE.md](docs/STAGE3_SUPABASE.md).
8. On an Android phone, open **Usage** and read its explanation. Enable usage totals, grant/revoke Usage Access in system Settings, choose apps, set a threshold, and verify a warning without app blocking. Turn usage off and confirm saved totals are erased. See [docs/STAGE4_ANDROID_USAGE.md](docs/STAGE4_ANDROID_USAGE.md). Usage reporting has not been exercised on a device unless this checklist is completed.
9. On an Android phone, open **Limits** and read the disclosure before enabling anything. Select a test app and a task rule, grant Accessibility access in Settings, then verify the overlay, task release, exception, pause, break, emergency bypass, off switch, permission revocation and recovery. See [docs/STAGE5_ANDROID_BLOCKING.md](docs/STAGE5_ANDROID_BLOCKING.md). Blocking has not been exercised on a device unless this checklist is completed.

## Architecture and privacy

- `android/` uses Kotlin/Jetpack Compose, a repository boundary and Room. WorkManager persists inexact one-time reminder work across app/process restarts and device reboots.
- `web/` uses TypeScript/Vite, IndexedDB for local data, and a service worker for the PWA shell and opt-in notifications.
- Task/check-in fields are documented in [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md). Android runs reminder logic on-device; web uses only browser notification APIs. Notification text is not sent to a third party.
- Sign-in and optional task/check-in sync use Supabase only when configured. The app makes no AI calls or background user tracking. Android usage totals are optional, stay on the device, and are never synced. Optional app blocking is off by default and uses an Android Accessibility service only after separate disclosure, user consent and Settings enablement. It checks foreground package names against the user's selection, does not inspect screen content or click controls, and is an aid that can be bypassed or disabled.
- Android’s WorkManager notifications may be delayed by battery-saving rules. Browser reminders cannot be relied on while the app page is closed or suspended by the browser.

## Accounts and sync (optional)

Create a Supabase project and apply [`supabase/migrations/20261025000000_stage3_sync.sql`](supabase/migrations/20261025000000_stage3_sync.sql). Follow [docs/STAGE3_SUPABASE.md](docs/STAGE3_SUPABASE.md) to configure email/password auth, per-user RLS, web/Android local configuration, redirect URLs, and the two-device manual checklist. Web configuration goes in ignored `web/.env.local`; Android configuration goes in ignored `android/local.properties` (copy `android/local.properties.example`). Use only the project URL and publishable key. Never commit a service-role key.

When configured, local changes queue on-device and sync on sign-in, launch, and reconnect. The latest `updated_at` wins; remote wins timestamp ties. Deletions are tombstones. Failed sync leaves local data intact and shows the sync state. Current sync supports tasks and daily check-ins; reminder schedules remain local. Cross-device sync has not been verified against a real Supabase project unless the two-device checklist has been run.

## Roadmap

1. Foundation and matching app shells (complete).
2. Local tasks, daily planning/check-ins and reminders (this stage).
3. Supabase authentication and conflict-aware sync.
4. Optional Android usage totals and non-blocking app limits (implemented; requires device verification). See [docs/STAGE4_ANDROID_USAGE.md](docs/STAGE4_ANDROID_USAGE.md).
5. Optional Android app blocking and final hardening (implemented; requires target-phone verification). See [docs/STAGE5_ANDROID_BLOCKING.md](docs/STAGE5_ANDROID_BLOCKING.md).
