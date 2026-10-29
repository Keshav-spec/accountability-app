-- Stage 6: richer planning metadata and week reflections; existing RLS policies remain in force.
alter table public.tasks
  add column if not exists category text not null default 'General',
  add column if not exists sort_order integer not null default 0,
  add column if not exists recurrence_rule text not null default 'none',
  add column if not exists series_id text;

alter table public.daily_check_ins
  add column if not exists weekly_reflection text;

alter table public.tasks drop constraint if exists tasks_recurrence_rule_check;
alter table public.tasks add constraint tasks_recurrence_rule_check
  check (recurrence_rule in ('none', 'daily', 'weekdays', 'weekly'));
