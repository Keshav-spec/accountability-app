# Steady Project Guide

This guide describes the current Steady repository: product purpose, technologies, architecture, features, data, setup, privacy, validation, and operational limitations. For the shortest setup, see the root README. For focused instructions, use the reference guides at the end.

## 1. Project summary

Steady is a local-first personal planning and accountability app for Android and the web. It combines a flexible task list, daily planning and check-ins, weekly reflection, gentle reminders, and optional Android-only usage totals and app pause controls. Its tone is supportive: a skipped task, a mixed day, and a rest day are valid outcomes.

Both clients remain usable without network access or Supabase credentials. Supabase is optional and synchronizes tasks and check-ins between authenticated devices. Android app usage details and reminder preferences remain local. The web app cannot inspect Android usage or pause Android apps.

The implementation has developed through six feature stages:

1. Shared Android and web experience.
2. Local task management, daily planning, check-ins, and reminders.
3. Supabase authentication, row-level security, and offline-capable synchronization.
4. Optional Android usage totals and non-blocking limits.
5. Optional, transparent Android app pause controls.
6. Recurring tasks, routine starters, categories, search/order, reminder presets/context, rest days, and weekly reflections.

“Implemented” means code exists in this repository. It does not assert that the live Supabase project or target phone has been validated. See Validation status.

## 2. Product features

### Task planning

Users can create, edit, complete, skip, reschedule, restore, or delete tasks. A task has a required title, optional notes, local scheduled date, optional target time, status, category, ordering value, and optional recurrence rule. Titles are trimmed and validated to 1–120 characters. Leaving time blank means “sometime that day.”

Recurrence supports none, daily, weekdays, and weekly. Completing or skipping an occurrence creates the next dated occurrence. Missed dates are not backfilled. Undoing the completion or skip that generated an occurrence marks that next occurrence deleted, so synchronization cannot restore it accidentally.

Task discovery includes title/notes/category search, category filtering, completion-state views, ordering, and quick moves to today or tomorrow. Categories are user-editable strings and default to General. Empty states point users to a first task or routine instead of injecting sample tasks into the plan.

### Routine starters

The Android and web clients provide morning and evening starter routines. A starter creates several ordinary tasks with suggested titles/categories. The generated tasks can be edited or deleted. These are reusable task templates, not fixed schedules or user-managed routine definitions.

### Daily and weekly check-ins

Morning planning supports up to three priorities plus an optional short intention. Progress check-in records whether the user started or wants a smaller next step, with optional text. Evening review saves a reflection and optional complete, mixed, or rest outcome. Weekly reflection is stored against Monday's date for the current week. Choosing rest does not change or remove tasks.

### Local reminders

Reminders are opt-in and can be disabled. Users can set times, quiet hours, daily limit, and snooze duration. Light, Spaced, and Steady presets populate an editable schedule. A user-selected short note can appear in reminder text. It stays on the device and is not uploaded.

Reminder logic supports snoozing, stopping for the day, quiet hours, daily caps, duplicate prevention across restarts, and suppression when today's tasks are completed or skipped. Android uses WorkManager and Android notifications; jobs are intentionally inexact and may be delayed by Android power rules. Web notifications require explicit permission; support varies and scheduled notifications may not fire while the page is closed or suspended.

### Android Usage dashboard and limits

Usage tracking is off until the user opens the feature and opts in. Steady explains Usage Access before directing the user to Android Settings. The dashboard uses Android UsageStatsManager daily aggregates for apps selected from launcher-visible apps. It shows app icon/name, daily total, and optional threshold. Thresholds create in-app warnings and never prevent app launches.

The app does not store a stream of raw usage events. The selected app list, thresholds, and aggregate totals remain on Android and are not synchronized. Disabling usage tracking erases locally saved aggregates. Totals can be delayed, rounded, or approximate; apps without launcher entries may not appear in the picker.

### Optional Android app pause

Blocking/pause behavior is off by default and requires affirmative user action plus Android Accessibility special access. The Accessibility service listens for window-state changes and checks only the foreground package name against the user's selected apps and exceptions. It does not inspect screen text/content, screenshots, notifications, or perform clicks/gestures. Observed app names are not uploaded.

Rules can pause a chosen app until today's selected tasks are complete/skipped or until a configured break time. Essential/system apps and user exceptions stay available. Controls include pause for 15 minutes, take a break, emergency bypass, and turn blocking off.

This is an accountability aid, not a security boundary. Users can bypass it, revoke Accessibility access, turn it off, or uninstall Steady. Android/OEM lifecycle behavior can stop or delay the service. The app provides a Settings-based recovery route and does not use root, device-admin privileges, boot persistence, or deceptive techniques. Current project docs assume local Android Studio/sideload distribution, not Google Play publication. A Play release needs current policy review and required Accessibility disclosure/declaration materials; approval is not guaranteed.

## 3. Technology inventory

| Component | Technology | Role |
| --- | --- | --- |
| Android language/UI | Kotlin 2.3.21, Jetpack Compose, Material 3, Compose BOM 2025.09.00 | Native screens and state-driven interface |
| Android build | Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin Compose and Serialization plugins, KSP 2.3.12 | Build, Kotlin compiler plugins, Room code generation |
| Android platform | compileSdk 36, targetSdk 36, minSdk 26, Java/Kotlin target 17 | Platform compatibility and language bytecode |
| Android local database | Room 2.8.5 over SQLite | Structured persistence, DAOs, explicit migrations |
| Android reminders | WorkManager 2.12.0, Notification APIs, POST_NOTIFICATIONS runtime permission on Android 13+ | Deferrable local scheduling and notifications |
| Android usage | UsageStatsManager, Usage Access AppOps check, targeted launcher package visibility | User-initiated usage totals and app picker |
| Android app pause | AccessibilityService, TYPE_ACCESSIBILITY_OVERLAY | Optional foreground package matching and pause surface |
| Android support | AndroidX Activity 1.13.0, Lifecycle 2.9.4, Core KTX 1.17.0, Ktor Android 3.4.3 | Compose hosting, lifecycle, platform and network support |
| Web language/build | TypeScript ~5.8.3, Vite 7 package range, ES modules | Browser client, type checks and production bundle |
| Web local database | IndexedDB | Guest and per-account local state |
| Web application shell | Web App Manifest, service worker, HTML/CSS | Installable PWA shell and notification support |
| Remote services | Supabase Auth, Supabase Postgres, Supabase JS 2.x, Supabase Kotlin 3.6.0 | Optional identity and authenticated task/check-in sync |
| Automated tests | JUnit 4; Node built-in test runner | Local rules, mapping, and model validation |

Dependency definitions are in android/build.gradle.kts, android/app/build.gradle.kts, web/package.json, and web/package-lock.json. When changing Gradle or the Android Gradle Plugin, recheck the Gradle JVM compatibility. The project compiles Kotlin/Java for JVM 17.

## 4. Repository structure

- android/app/src/main/java/com/steady/accountability/MainActivity.kt: Android entry point, app state, primary navigation, and several Compose screens.
- android/app/src/main/java/com/steady/accountability/AccountScreen.kt: Android account and profile UI.
- android/app/src/main/java/com/steady/accountability/data/: Room entities, DAOs, repositories, sync mapping, task/check-in rules, and account archive support.
- android/app/src/main/java/com/steady/accountability/reminders/: local scheduling, notification worker, and notification action receiver.
- android/app/src/main/java/com/steady/accountability/usage/: usage query repository and thresholds/permission rules.
- android/app/src/main/java/com/steady/accountability/blocking/: Accessibility service, blocking rules, and pause UI/preferences.
- android/app/src/main/res/: Android manifest resources, Accessibility service metadata, strings, and application icon.
- android/app/src/test/: Android local unit tests.
- web/src/main.ts: web screen composition, user interactions, account flow, and local state operations.
- web/src/localRepository.ts: IndexedDB persistence, per-account keys, and old-state defaults.
- web/src/models.js and models.d.ts: task/check-in types and date/status helpers.
- web/src/reminders.js: browser reminder scheduling rules.
- web/src/syncRules.js: sync row conversion and merge/conflict helpers.
- web/src/supabaseSync.ts: Supabase client, auth helpers, and synchronization.
- web/src/*.css: shared and feature-specific styling.
- web/public/: PWA manifest, service worker, and icons.
- supabase/migrations/: versioned SQL schema and RLS policy changes.
- docs/: product requirements, setup, privacy, platform guidance, and manual checks.
- stitch_modern_app_ui_redesign/: visual design references used during Android redesign work.
- README.md: quick start and project landing page.

Some Android screens remain in MainActivity.kt. Further file separation is possible without changing the data contract.

## 5. Architecture and data flow

### Local-first flow

1. The user edits a task or check-in.
2. The client writes it to local storage first.
3. The app remains usable offline; pending remote changes stay local.
4. When authenticated and online, the sync layer pulls remote rows, compares timestamps, pushes local pending rows, and persists the resolved local state.
5. If a network request fails or the session expires, local records remain and the interface can show offline/needs-attention status.

### Android storage

Room entities include TaskEntity, DailyCheckInEntity, ReminderSettingsEntity, reminder history, usage settings, and usage aggregates. DAOs provide owner-scoped reads/writes. TaskRepository and CheckInRepository provide UI-facing operations and recurrence/check-in persistence.

AppDatabase registers additive migrations. The current Room schema is version 7. Migration 6→7 adds task category, sort order, recurrence rule, series ID, weekly reflection, and reminder context. Avoid destructive database fallback because this contains user data.

SupabaseSync maps tasks and daily check-ins to remote rows. Usage values, reminder settings/history, and app pause settings are not synced. AccountArchive provides local export/backup capabilities; encrypted Android snapshots depend on the installation's Keystore key and are not cloud backups.

### Web storage

LocalRepository stores application state in IndexedDB. State is split by guest and signed-in account keys. Migration/default logic adds missing fields when loading older data. Supabase sync runs separately from local persistence.

### Supabase schema

The base migration defines user_profiles, tasks, daily_check_ins, and reminder_preferences. The Stage 6 migration adds task category/order/recurrence/series fields and weekly_reflection. RLS is enabled and policies constrain select/insert/update to rows with user_id equal to auth.uid(). Client roles do not receive hard-delete privileges. Tasks/check-ins use deleted_at tombstones.

## 6. Data models

### Task fields

- id: stable task/occurrence identifier.
- title: required trimmed text, 1–120 characters.
- notes: optional user text.
- scheduledDate: ISO local calendar date YYYY-MM-DD.
- targetTime: optional local HH:MM time; absent means anytime on that date.
- status: planned, completed, or skipped.
- category: free-form category; defaults to General.
- sortOrder: ordering among same-day tasks.
- recurrenceRule: none, daily, weekdays, or weekly.
- seriesId: recurrence-series identifier for generated occurrences.
- createdAt and updatedAt: timestamps for record lifecycle and conflict resolution.
- deletedAt: nullable soft-delete marker.
- ownerId/user_id: local and remote account ownership.

### Check-in fields

A check-in stores date, up to three priorities, optional morning intention, progress note, evening reflection, optional weekly reflection, optional day outcome (COMPLETE, MIXED, REST), created/updated timestamps, optional deletion marker, pending-sync state locally, and owner scope. Weekly reflection uses a row keyed by Monday's date.

### Reminder fields

Reminder configuration includes enabled state, selected times, quiet hours, daily limit, snooze length/deadline, stop-for-day date, delivered date/count/slot keys used to avoid duplicate sends, and optional reminder context. These values remain client-local.

### Android usage and pause preferences

Selected app packages, display labels, thresholds, local daily aggregate milliseconds, pause rules, exceptions, and bypass timers remain Android-local. These are not part of Supabase tables or web sync.

## 7. Authentication and synchronization

### Authentication

The clients support Supabase email/password sign-in and account creation with a display name, Google OAuth through Supabase, password reset/recovery, Google account linking when Supabase manual linking is enabled, and sign-out. Google OAuth requires Google Cloud client configuration and Supabase callback/redirect configuration. Google client secrets belong in Supabase provider configuration, not in either app.

### Sync lifecycle and failure handling

Sync may run after sign-in, app launch, explicit refresh, or network reconnect. Local writes happen before remote network calls. Failed uploads leave the local record intact for retry. UI sync states include synced, syncing, offline, and needs attention.

### Conflict rule

Synchronization uses whole-record last-write-wins based on updated_at. The newest timestamp wins; exact ties resolve to the remote record for deterministic convergence. A database trigger makes remote timestamps monotonic. There is no field-level merge or conflict review UI; two concurrent changes to separate fields of one record can overwrite one another. Tombstones retain deletion state across devices.

### Account isolation

Each client stores separate guest and per-account local state. Android filters DAO queries by owner ID; web uses distinct IndexedDB keys. Legacy pre-isolation data cannot be separated retroactively by account. Upgrade behavior assigns it using the previously saved sync owner or the documented unowned-data claim path. Read the Stage 3 guide before changing accounts on an installation with older mixed data.

## 8. Privacy and security

- Tasks/check-ins stay local unless the user configures Supabase sign-in/sync.
- No AI calls are made. Notification content is not sent to a third-party notification service.
- The app does not implement background behavioral tracking, location, contacts, message access, notification reading, or browser-content inspection.
- Usage Access is opt-in and used for selected-app daily aggregates. Usage data is not synced.
- Accessibility is separately opt-in and used only by the Android app pause feature to compare the foreground package name with the selected packages. No screen text is read and observed names are not uploaded.
- Reminder permission is requested only after a clear user action. Reminder preferences and history remain local.
- web/.env.local and android/local.properties are ignored by Git. The Supabase publishable/anon key is intended for client use but relies on RLS for data security. Never put a service-role secret in either client.
- The app pause feature is bypassable. It must not be described as security enforcement or impossible to bypass.

## 9. Setup and configuration

### Web

Requirements: Node.js 20+ and npm.

    cd web
    npm install
    Copy-Item .env.example .env.local
    npm run dev

Set VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY in web/.env.local when accounts/sync are desired. Without them, local use is available. Restart the development server after changing environment settings. Use npm run build for type checking and production bundling; npm run preview serves a production bundle.

### Android

Requirements: Android Studio, JDK 17, Android SDK Platform 36, and an API 26+ device/emulator. Open the android directory in Android Studio and let Gradle sync. The project wrapper is Gradle 8.13 and AGP is 8.13.2.

Copy android/local.properties.example to android/local.properties. Preserve or set sdk.dir to the installed SDK path. To configure sync, add supabase.url and supabase.publishableKey. Both clients use the same Supabase project URL and publishable/anon key. Do not store a Google OAuth secret or Supabase service-role key there.

Choose the app run configuration, select an emulator or USB-connected phone, and run. For USB use, enable Developer options and USB debugging on Android, accept the phone's debugging authorization prompt, and select it in Android Studio. Ensure the Gradle JVM configured by Android Studio is compatible with Gradle 8.13.

### Supabase

1. Create a Supabase project and enable the required Auth providers.
2. Run supabase/migrations/20261025000000_stage3_sync.sql in the SQL editor.
3. Run supabase/migrations/20261029000000_stage6_daily_planning.sql after the base schema.
4. Configure Site URL and allowed redirects for web origins and steady://login-callback for Android.
5. For Google, create/configure OAuth credentials in Google Cloud, register the Supabase callback URI, and place the OAuth provider secret only in Supabase Auth settings.
6. Configure each client locally and rebuild/restart it.
7. Verify RLS and run the two-account/two-device checklist before relying on cloud sync.

See Stage 3: Supabase setup and sync for full provider, redirect, RLS, recovery, and validation steps.

## 10. Build and test commands

### Web

    cd web
    npm test
    npm run build

The tests use Node's built-in runner for task/model behavior, reminder rules, and sync mapping/conflict helpers. The build runs TypeScript project compilation and Vite.

### Android

Run :app:testDebugUnitTest or :app:assembleDebug from Android Studio. With a Gradle 8.13 executable available:

    gradle -p android testDebugUnitTest
    gradle -p android assembleDebug
    gradle -p android installDebug

Android local tests cover task validation/rules, reminder policy, sync rules, usage thresholds/date boundaries/access states, and app pause rules. These tests do not exercise real Usage Access settings, Accessibility overlays, notification delivery timing, or OEM-specific recovery.

## 11. Migrations and backups

- Android Room model changes require a schema version bump, additive migration, migration registration, and upgrade validation from supported versions. Do not discard existing user data.
- Web IndexedDB changes must retain guest and per-account keys and supply safe defaults for older state.
- Supabase schema changes require a new versioned SQL migration. Keep RLS enabled, apply migrations before clients depend on new columns, and test ownership boundaries.
- Update both Android and web row mappings when synchronized fields change. An older remote schema can cause sync to need attention while the local copy remains available.
- Make a backup before major upgrades. Android encrypted archive snapshots are tied to that installation's Keystore key; browser exports and Supabase data have distinct restore behavior.

## 12. Known limitations and non-goals

- Android WorkManager reminders are inexact; exact-alarm access is not requested. OS permission or battery restrictions can prevent or delay notifications.
- Web reminders vary by browser/OS and may not fire while the page is closed or suspended.
- Android UsageStats totals can be approximate or delayed, and manufacturer policy can restrict access.
- The Android pause feature can be bypassed, disabled, or uninstalled; it depends on system Accessibility service behavior.
- Supabase sync requires a correctly configured project and network for remote updates. Local use does not depend on sync.
- Conflicts are whole-record last-write-wins, not field-level merge or human resolution.
- Reminder preferences/history and Android usage/pause settings do not sync.
- There is no AI planning, third-party push delivery, location tracking, or background behavior analytics.

## 13. Validation status and release checklist

A successful local build does not prove external OAuth configuration, RLS, cross-device sync, or device-only permissions work on target hardware. Before relying on a release, complete:

- [ ] Web tests and production build.
- [ ] Android unit tests and debug build.
- [ ] Supabase migrations applied; RLS tested with two separate users.
- [ ] Android-to-web and web-to-Android task/check-in sync, including offline edits and deletion.
- [ ] Google sign-in, identity linking, and password recovery on intended clients.
- [ ] Android reminder permission, quiet hours, snooze, daily limit, restart, and disable behavior.
- [ ] Physical-device Usage Access grant/revoke, daily total, threshold, and aggregate deletion.
- [ ] Physical-device Accessibility grant/revoke, selected-app rule, task release, pause/break/bypass, service recovery, reboot/update, and uninstall.
- [ ] Current Google Play policy and declaration requirements checked before Play distribution.

Do not claim end-to-end Supabase or device-level usage/blocking validation until the relevant live project/device checklist has been completed.

## 14. Reference documents

- [Root README and quick start](../README.md)
- [Stage 3: Supabase, RLS, OAuth, sync, and recovery](STAGE3_SUPABASE.md)
- [Stage 4: Android Usage Access, totals, and limits](STAGE4_ANDROID_USAGE.md)
- [Stage 5: Android app pause, disclosure, recovery, and distribution](STAGE5_ANDROID_BLOCKING.md)
- [Stage 6: recurring tasks, routine starters, reminder presets, and weekly reflection](STAGE6_DAILY_PLANNING.md)
- [Product requirements and privacy boundaries](PRODUCT_REQUIREMENTS.md)

