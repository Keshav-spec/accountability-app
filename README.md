# Steady

**A local-first personal planner for Android and the web.** Steady brings tasks, daily planning, check-ins, and gentle reminders together, with optional account sync and user-controlled Android usage tools.

<p>
  <a href="https://github.com/Keshav-spec/accountability-app"><img src="https://img.shields.io/badge/Project-GitHub-24292f?style=for-the-badge" alt="Project on GitHub"></a>
  <a href="docs/STAGE3_SUPABASE.md"><img src="https://img.shields.io/badge/Setup-Accounts%20%26%20Sync-3f6b58?style=for-the-badge" alt="Accounts and sync setup"></a>
  <a href="docs/STAGE4_ANDROID_USAGE.md"><img src="https://img.shields.io/badge/Guide-Usage%20Dashboard-789b83?style=for-the-badge" alt="Android usage guide"></a>
  <a href="docs/STAGE5_ANDROID_BLOCKING.md"><img src="https://img.shields.io/badge/Guide-App%20Limits-244237?style=for-the-badge" alt="Android app limits guide"></a>
</p>

## Overview

Steady supports a calm daily routine across an Android app and a browser-based web app. Both clients work offline and store data locally. Supabase is optional and adds sign-in plus synchronization for tasks and daily check-ins.

### Features

- Create, edit, complete, skip, and reschedule tasks.
- Plan up to three daily priorities, record progress, and save an evening reflection.
- Configure local reminders, quiet hours, snoozes, and daily limits.
- On Android, optionally view selected apps' daily usage totals and set non-blocking warning thresholds.
- On Android, optionally enable an app pause overlay for chosen apps, with task-based or break-time rules and clear bypass controls.
- Sign in and synchronize tasks and check-ins across devices when Supabase is configured.

## Project status

| Component | Status | Notes |
| --- | --- | --- |
| Android app | Implemented | Kotlin, Jetpack Compose, Room, WorkManager; Android API 26+ |
| Web app | Implemented | TypeScript, Vite, IndexedDB, installable PWA shell |
| Local-first storage | Implemented | Works without a network connection or Supabase credentials |
| Supabase sign-in and sync | Implemented | Syncs tasks and daily check-ins; real-project two-device validation is a separate manual step |
| Android usage totals | Implemented | Optional special Usage Access; device results may be delayed or approximate |
| Android app blocking | Implemented | Optional Accessibility service; requires real-device verification and can be bypassed |

## Quick start

### Web app

**Requirements:** Node.js 20 or later and npm.

```powershell
cd web
npm install
npm run dev
```

Open the local address printed by Vite. Common commands:

```powershell
npm test       # Run web logic tests
npm run build  # Type-check and build the production PWA
npm run preview
```

Browser notifications and service workers require HTTPS or localhost. Notification delivery depends on browser and operating-system support; reminders may not run while the page is closed or suspended.

### Android app

**Requirements:** Android Studio, JDK 17, Android SDK Platform 36, and a connected Android API 26+ device or emulator. The project uses Gradle 8.13, Android Gradle Plugin 8.13.2, and Kotlin 2.3.21.

Open the `android/` directory in Android Studio, allow the Gradle sync to finish, select the `app` run configuration, and run it on a device or emulator. Set the Android SDK location in `android/local.properties` as `sdk.dir`, or configure `ANDROID_HOME`.

From a terminal with Gradle 8.13 available:

```powershell
gradle -p android testDebugUnitTest
gradle -p android assembleDebug
gradle -p android installDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. Android notifications require notification permission on Android 13 and later. Steady explains the request before opening the system prompt.

## Optional Supabase setup

Supabase is only needed for accounts and cross-device task/check-in sync. Local task, check-in, reminder, and usage features remain available without it.

1. Create a Supabase project and apply [`supabase/migrations/20261025000000_stage3_sync.sql`](supabase/migrations/20261025000000_stage3_sync.sql).
2. Follow [the Supabase setup guide](docs/STAGE3_SUPABASE.md) to configure email authentication, row-level security, redirect URLs, and local client settings.
3. Copy `web/.env.example` to the ignored `web/.env.local`; set `VITE_SUPABASE_URL` and `VITE_SUPABASE_PUBLISHABLE_KEY`.
4. Copy `android/local.properties.example` to the ignored `android/local.properties`; keep the correct `sdk.dir` and add `supabase.url` and `supabase.publishableKey`.
5. Restart the web development server and rebuild Android after changing configuration.

Use only the Supabase project URL and publishable (or legacy anon) key in client apps. Never place a service-role key in either app. Review the two-device and row-level-security checklist before relying on sync; automated tests do not validate a live Supabase project.

## Privacy and platform behavior

- Android stores tasks, check-ins, reminder settings/history, and usage selections locally in Room. The web app stores local data in IndexedDB.
- Supabase sync is optional and currently covers tasks and daily check-ins. Reminder delivery settings and Android usage totals remain local.
- Usage totals are off until enabled. Android requires the user to grant Usage Access in system Settings. Totals can be delayed or approximate, and turning usage tracking off erases stored aggregates.
- App blocking is separately opt-in and off by default. After the user reads a disclosure and enables Android Accessibility access, Steady checks the foreground package name against the selected apps. It does not inspect screen text or click controls. Blocking is an accountability aid, not a security boundary; it can be paused, bypassed, disabled, or uninstalled.
- No AI calls are made. Notification content and Android usage data are not sent to third parties. Steady does not perform background user tracking.
- Android WorkManager reminders are inexact and may be delayed by battery or background restrictions. Browser notification behavior varies by browser and platform.

## Verification guides

- [Stage 3: Supabase setup and sync checklist](docs/STAGE3_SUPABASE.md)
- [Stage 4: Android usage access and device checklist](docs/STAGE4_ANDROID_USAGE.md)
- [Stage 5: Android app blocking, recovery, and device checklist](docs/STAGE5_ANDROID_BLOCKING.md)
- [Product requirements and privacy boundaries](docs/PRODUCT_REQUIREMENTS.md)

Before a release, exercise the relevant manual checklist on the intended devices. This repository does not claim that Supabase sync has been validated against a live project or that Android usage reporting and blocking have been verified on a particular phone.

## Repository layout

```text
android/    Android application and Room database
web/        TypeScript web application and PWA
supabase/   Database migrations and row-level-security schema
docs/       Setup, product, privacy, and device verification guides
```
