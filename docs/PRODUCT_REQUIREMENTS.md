# Product requirements — Steady

## Purpose and audience

Steady is a local-first, single-user planner for a person who wants gentle structure during a week at home, across an Android phone and Windows 11 laptop. It should feel calm and encouraging; rest is a valid choice.

## Data models

- **Task**: `id`, `title`, optional `notes`, `scheduledDate` (ISO local date), optional `targetTime` (24-hour local time), `status` (`planned`, `completed`, `skipped`), `createdAt`, `updatedAt`.
- **Daily check-in**: `date`, up to three `priorities`, optional `morningPlan`/intention, optional `progressNote`, optional `eveningReflection`, optional `dayOutcome` (`COMPLETE`, `MIXED`, `REST`), `createdAt`, `updatedAt`.
- **Reminder settings**: enabled flag, selected local times, quiet-hours start/end, daily limit, snooze duration, stop-for-day date, snooze deadline, delivered date/count and delivered slot keys.

## Stage 2 behavior

- Tasks can be added, edited, completed, skipped and rescheduled. Status can be undone. Home groups today, upcoming and today's completed/skipped tasks.
- Morning planning saves at most three priorities and a short intention. Progress check-in records whether the user started or wants a smaller step. Evening review saves a reflection and optional Complete/Mixed/Rest status.
- Reminders default off. They run only when there is at least one planned task today, respect quiet hours, snooze, stop-for-day and a daily cap, and persist delivery keys to avoid duplicate sends after restart. Completing or skipping all today's tasks cancels future reminders.
- Android uses WorkManager with inexact delayed work and Android notifications. The notification permission is requested on API 33+ only after the user chooses to enable reminders and sees a plain-language explanation.
- Web notification permission is requested only from an explicit button. Reminders are scheduled locally while the page is open; browser scheduling can be delayed or stopped by browser/OS lifecycle rules.
- Android stores tasks, check-ins and reminder preferences in Room. The web app stores local data in IndexedDB. Repository boundaries keep later remote sync separate from UI code.

## Stage 3 behavior

- Supabase email/password auth is optional. Local Room/IndexedDB data remains available without credentials or network.
- Tasks and daily check-ins use owner-scoped Supabase tables protected by RLS. Pending edits remain local until writes succeed; sync runs on sign-in, launch, explicit refresh, and reconnect.
- The newest `updated_at` wins; remote wins exact timestamp ties. Task deletion uses a retained `deleted_at` tombstone. See `docs/STAGE3_SUPABASE.md` for schema and operational details.
- Reminder schedules and delivery state remain local in Stage 3. That stage included no AI calls, background tracking, app usage permissions, or app blocking. Stage 4 adds a separate, opt-in, foreground-only Android usage feature described below.

## Stage 4 behavior

- Android usage totals are off until the user opens Usage, reads the disclosure, and enables the feature. Android’s special Usage Access is granted by the user in system Settings, not by a runtime permission dialog.
- Usage is queried only while the Usage dashboard is open or explicitly refreshed. There is no foreground service, accessibility service, polling worker, or background monitor.
- The app picker is limited to launcher-visible apps through a targeted `<queries>` declaration; it does not request broad `QUERY_ALL_PACKAGES` visibility. Apps without a launcher entry may not appear.
- Only selected apps’ daily duration aggregates, chosen-app flags and optional thresholds are stored in Room. Usage data is never synchronized. Turning usage off deletes all saved daily usage aggregates; app choices and limits remain local.
- A threshold produces a gentle in-app dashboard warning when the user next views or refreshes Usage. It never blocks or intercepts app launches.

## Stage 5 behavior

- App blocking is off by default. The user selects launcher-visible apps and chooses either a rule tied to today's selected planned tasks (complete or skip all to release) or a local break time.
- Before enabling, the app explains that Android Accessibility access is required, that only the foreground package name is checked against the user's selected apps, and that screen text/content is not read, clicks are not performed, and package activity is not uploaded.
- The enabled service observes only window-state changes and uses a full-screen Accessibility overlay when a selected app matches an active rule. It does not launch activities or trap the user in Steady. Steady itself and listed system app packages are always allowed; the user can add exceptions.
- The overlay offers Pause for 15 minutes, Take a break, Emergency bypass until tomorrow, and Turn blocking off. Settings also provides explicit pause, break, bypass, and off controls. This is an accountability aid, not a security boundary; the user can revoke access, pause, bypass or uninstall.
- Service use is not started by the app until the user enables blocking and turns on the service in Android Settings. There is no hidden boot receiver, device-admin access, root use, usage of screen contents, or battery restriction evasion. Android/system lifecycle rules may stop or delay the service; users can restore it from the Limits tab and Accessibility settings.
- Reminder notifications stay local, obey configured quiet hours, limits, snoozes and stop-for-day, and retain a bounded recent history explaining the reason each notification appeared.
- Current Android distribution instructions use Android Studio/`installDebug`; no Google Play release is assumed. A future Play submission requires policy review, Accessibility declaration and disclosure/listing materials before release.

## Privacy boundaries

- No task or reminder data goes to an AI model. Task/check-in sync goes only to the configured Supabase project. Notification content stays on the device/browser.
- No background user tracking, messages, contacts, location, browser history, social features or subscriptions. Android Usage Access is optional, foreground-only, and user initiated. Accessibility is used only for the separately opted-in Stage 5 blocker as described above. Sign-in credentials are handled by Supabase Auth and are not written to task/check-in storage.
- Reminders are opt-in, configurable and disableable. The user can snooze or stop them for the day. Android never requests exact-alarm access; no precise-to-the-minute delivery promise is made.
- Web reminders cannot reliably fire when the app page is closed or suspended. Browser/platform support varies.
- Secrets stay out of source control. Client tables require ownership-scoped Row Level Security.

## Staged roadmap

1. Foundation and matching Android/web shells (complete).
2. Local task management, planning/check-ins and reminders (current).
3. Supabase authentication, private schema, offline sync queue and conflict policy (implemented; requires configured-project and two-device validation).
4. Optional Android usage totals and non-blocking app limits, with explicit opt-in and plain-language permission explanation (implemented; requires device validation).
5. Optional Android app blocking, transparent foreground-package matching and recovery controls (implemented; requires target-phone validation). See `docs/STAGE5_ANDROID_BLOCKING.md`.

## Platform/dependency choices

- Android minimum API 26, compile/target API 36, AGP 8.13.2, Gradle 8.13, Kotlin 2.3.21, Compose BOM 2025.09.00, Room 2.8.5, WorkManager 2.12.0, Core KTX 1.17.0, Lifecycle Runtime KTX 2.9.4. The minimum Android API follows the planned Supabase Kotlin client’s documented minimum without desugaring.
- WorkManager is used because it persists deferrable work across app restarts/reboots. It is inexact: Android battery and background rules may delay a reminder.
- Web uses Vite 7, TypeScript and IndexedDB locally, with optional Supabase Auth/Postgres sync when configured. Notification permission and persistent notification delivery depend on browser support.
