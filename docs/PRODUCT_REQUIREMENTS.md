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
- Reminder schedules and delivery state remain local in this stage. There are no AI calls, background tracking, app usage permissions, or app blocking.

## Privacy boundaries

- No task or reminder data goes to an AI model. Task/check-in sync goes only to the configured Supabase project. Notification content stays on the device/browser.
- No background user tracking, app usage access, accessibility service, app blocking, messages, contacts, location, browser history, social features or subscriptions. Sign-in credentials are handled by Supabase Auth and are not written to task/check-in storage.
- Reminders are opt-in, configurable and disableable. The user can snooze or stop them for the day. Android never requests exact-alarm access; no precise-to-the-minute delivery promise is made.
- Web reminders cannot reliably fire when the app page is closed or suspended. Browser/platform support varies.
- Secrets stay out of source control. Client tables require ownership-scoped Row Level Security.

## Staged roadmap

1. Foundation and matching Android/web shells (complete).
2. Local task management, planning/check-ins and reminders (current).
3. Supabase authentication, private schema, offline sync queue and conflict policy (implemented; requires configured-project and two-device validation).
4. Optional Android usage totals and selected-app restrictions, with explicit opt-in, plain-language permission explanation, pause/break flow and emergency bypass.

## Platform/dependency choices

- Android minimum API 26, compile/target API 36, AGP 8.13.2, Gradle 8.13, Kotlin 2.3.21, Compose BOM 2026.09.00, Room 2.8.5, WorkManager 2.12.0, Core KTX 1.19.1, Lifecycle Runtime KTX 2.11.0. The minimum Android API follows the planned Supabase Kotlin client’s documented minimum without desugaring.
- WorkManager is used because it persists deferrable work across app restarts/reboots. It is inexact: Android battery and background rules may delay a reminder.
- Web uses Vite 7, TypeScript and IndexedDB locally, with optional Supabase Auth/Postgres sync when configured. Notification permission and persistent notification delivery depend on browser support.
