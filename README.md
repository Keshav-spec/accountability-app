# Steady — personal accountability

Steady is a calm, single-user planner for Android and Windows. Stage 2 adds local task management, daily planning/check-ins and opt-in local reminders. It works without Supabase or a network connection.

## Prerequisites

- Android Studio, JDK 17, Android SDK Platform 36, and Gradle 8.13 (AGP 8.13.2, Kotlin 2.3.21).
- Node.js 20 or newer and npm (web app uses Vite 7).
- No Supabase account or credentials are needed for local use.

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

Alternatively, open `android/` in Android Studio, configure Gradle 8.13 and select the `app` run configuration. The Android app stores tasks, check-ins, reminder preferences and delivery keys in Room. Android notifications require the notification permission on API 33 and later; the app explains this before opening the system prompt.

## Manual test checklist

1. Create a task with a title, notes, date and optional target time. Try a blank title, edit it and move it to a future date.
2. Complete a task, undo it, skip it, and confirm today’s done/skipped list and upcoming list update.
3. Add up to three morning priorities and an intention; save a progress check-in and evening reflection with Complete, Mixed or Rest. Reopen the app and confirm they remain.
4. Configure reminder times, quiet hours and a daily limit. Enable reminders; confirm Android’s permission prompt (API 33+) or the browser’s explicit permission prompt. On Android, wait for a reminder and try Snooze and Stop today. In the browser, keep the app page open until a test reminder time and try the notification actions.
5. Confirm quiet hours suppress or defer a reminder, completed/skipped tasks do not get further reminders, the daily limit is observed, and disabling reminders stops future reminders.
6. Restart each app and confirm local tasks, check-ins and reminder settings remain. Try offline after the first web load.

## Architecture and privacy

- `android/` uses Kotlin/Jetpack Compose, a repository boundary and Room. WorkManager persists inexact one-time reminder work across app/process restarts and device reboots.
- `web/` uses TypeScript/Vite, IndexedDB for local data, and a service worker for the PWA shell and opt-in notifications.
- Task/check-in fields are documented in [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md). Android runs reminder logic on-device; web uses only browser notification APIs. Notification text is not sent to a third party.
- This stage has no sign-in, remote sync, AI, tracking, app usage access, or app blocking.
- Android’s WorkManager notifications may be delayed by battery-saving rules. Browser reminders cannot be relied on while the app page is closed or suspended by the browser.

## Supabase and Stage 3

Supabase is not connected yet. When preparing Stage 3, create a project and configure its URL and publishable key locally as `VITE_SUPABASE_URL` and `VITE_SUPABASE_PUBLISHABLE_KEY` in `web/.env.local`; see [web/.env.example](web/.env.example). Android values belong in ignored local Gradle configuration. Never commit credentials. Stage 3 must first define private per-user tables, Row Level Security, sync queue behavior and conflict resolution before either client sends local data remotely.

## Roadmap

1. Foundation and matching app shells (complete).
2. Local tasks, daily planning/check-ins and reminders (this stage).
3. Supabase authentication and conflict-aware sync.
4. Optional Android usage totals and selected-app controls, only with explicit opt-in and clear permission explanations.
