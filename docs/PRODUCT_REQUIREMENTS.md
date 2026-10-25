# Product requirements — Steady

## Purpose and audience

Steady is a personal accountability tool for one person who wants gentle structure during a week at home, across an Android phone and Windows 11 laptop. It should feel calm and encouraging, never punitive. Stage 1 is a product skeleton; it runs without accounts or network access.

## Core concepts

- **Task**: `id` (stable string), `title`, optional `notes`, `scheduledDate` (ISO local date), optional `targetTime` (24-hour local time), `status` (`planned`, `completed`, `skipped`), `createdAt`, `updatedAt` (ISO timestamps).
- **Daily check-in**: `date` (ISO local date), optional `morningPlan`, optional `eveningReflection`, `createdAt`, `updatedAt`.
- A person can create a small set of daily tasks, complete or skip them, and reschedule them. A future sync layer must preserve these actions across both clients.

## Stage 1 delivered scope

- Android Kotlin/Jetpack Compose shell with Home, Tasks, and Check-in navigation and local Room storage.
- Responsive web shell/PWA with matching navigation and local browser storage.
- Empty states and safe sample tasks, with task creation and completion for a usable local-only demo.
- Basic models and validation tests; setup and architecture notes.
- Supabase setup choices documented; no credentials required for placeholder screens.

## Later feature roadmap

1. Add Supabase authentication, per-user task/check-in tables, Row Level Security, and sync with offline mutation queue and conflict policy.
2. Add morning planning, a few scheduled progress check-ins, evening reflection, and configurable reminders, quiet hours, daily caps, snooze, stop-for-today, and completion/skip stopping rules.
3. Add opt-in usage totals for the user's own dashboard and limits.
4. Add opt-in restrictions for only the apps the user selects, including a clear pause/break flow and emergency bypass.

## Privacy and safety boundaries

- Personal use only. No social features, public leaderboards, subscriptions, or AI chat/calls.
- No task or usage data is sent to an AI model.
- Do not collect passwords, message contents, contacts, location, or browser history.
- Usage tracking and app blocking are not in Stage 1. They must be opt-in, transparent, and narrowly scoped.
- Before any sensitive Android permission flow, explain in plain language what is needed and why, then let the user choose whether to open Settings.
- Keep secrets out of source control. Use ignored local configuration and a publishable Supabase key only in clients; enforce ownership with database policies.
- Stage 1 stores data only on the device/browser. Browser storage is separate from Room and is not yet synchronized.

## Unresolved setup choices

- Supabase project URL and publishable key, project region, and authentication method.
- Final table schema, deletion behavior, conflict resolution and initial sync strategy.
- Product decisions for the user's reminder schedule and selected apps; do not infer them.

## Platform and dependency choices

- Android minimum API is 26, matching Supabase Kotlin client's documented minimum without desugaring. Target/compile API is 36.
- Android versions selected from official stable documentation on 2026-10-25: AGP 8.13.2 / Gradle 8.13, Kotlin 2.3.21, Compose BOM 2026.09.00, Room 2.8.5, and KSP 2.3.12. Kotlin 2.3 fits AGP 8.13's documented compatibility range; AGP 8.13.2's R8 supports Kotlin 2.3. The project keeps to this established toolchain line to keep the setup simple for a beginner.
- Web uses Vite 7 and TypeScript; it avoids a frontend framework until product screens outgrow the small shell.

## Stage 1 acceptance

Both shells start locally with the README instructions, show the same calm product concept, and support an offline local demo. Models are documented and tested. There are no secrets, sign-in, remote sync, usage access, accessibility service, app blocking, AI calls, or push notifications.
