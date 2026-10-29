# Stage 6: Daily planning improvements

Steady now supports recurring tasks, task categories and ordering, reusable routine starters, reminder presets and custom reminder notes, weekly reflections, and clearer empty states on Android and web.

## Local data

Task metadata and weekly reflection fields are included in the local data stores. Android upgrades Room version 6 to 7 using a migration that adds columns with defaults; existing tasks and check-ins are retained. The web app fills defaults when it reads older IndexedDB state. Reminder settings remain local to each client.

## Supabase upgrade

If the project uses Supabase sync, apply `supabase/migrations/20261029000000_stage6_daily_planning.sql` in the Supabase SQL editor after the Stage 3 schema. It adds task category, order, recurrence, series identifiers, and the weekly reflection column. Existing RLS policies continue to scope the rows to their authenticated owner. The clients retain their local changes if sync is unavailable; until this migration is applied, the new columns will cause sync to need attention.

## Behavior

- A recurring task creates the next dated occurrence when the current occurrence is completed or skipped. Undoing that status change removes the generated occurrence using a deletion marker so it cannot reappear during sync.
- Routines are editable starter task sets, not locked schedules. Users can change or delete any generated task.
- Empty target times mean “sometime that day.” Categories, search, and ordering only organize the user's plan.
- Reminder presets populate times, snooze, and daily limit; users can edit them before saving. Custom reminder notes are shown locally with reminders and are not sent to a third party.
- Weekly reflections are stored with the Monday date for that week. Daily outcome options include rest; choosing rest does not alter or delete tasks.

## Manual review

1. Add a task with no target time, a category, and each recurrence option. Complete or skip it and confirm the next occurrence is created on the expected date. Undo and check that the generated occurrence is removed.
2. Search by title, note, and category. Filter categories; reorder tasks; move one to today and tomorrow.
3. Apply each routine starter and confirm its tasks can be edited and removed.
4. Choose a reminder preset, edit the note and schedule, save, and check a delivered reminder. Confirm quiet hours, snooze, stop-for-day, and daily limit still apply.
5. Save a weekly reflection, restart the app, and confirm it remains. Mark a day as rest and verify that planned tasks remain unchanged.
6. If signed in, apply the migration in Supabase and verify the updated task fields and weekly reflection sync between Android and web while each account remains isolated.

Web notification delivery requires an open app context and varies by browser and operating system. Android WorkManager schedules are inexact and may be delayed by power management. Recurrence generates the next occurrence only when the current occurrence is completed or skipped; it does not backfill missed dates.
