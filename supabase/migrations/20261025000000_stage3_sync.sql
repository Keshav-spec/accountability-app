-- Stage 3: private, user-owned data. Soft-deletes remain visible to sync clients as tombstones.
create table if not exists public.user_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  settings jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.tasks (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null check (length(trim(title)) between 1 and 120),
  notes text,
  scheduled_date date not null,
  target_time time,
  status text not null check (status in ('planned', 'completed', 'skipped')),
  created_at timestamptz not null,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists tasks_user_updated_idx on public.tasks(user_id, updated_at);

create table if not exists public.daily_check_ins (
  user_id uuid not null references auth.users(id) on delete cascade,
  date date not null,
  morning_plan text,
  priorities text[] not null default '{}',
  progress_note text,
  evening_reflection text,
  day_outcome text check (day_outcome is null or day_outcome in ('COMPLETE', 'MIXED', 'REST')),
  created_at timestamptz not null,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  primary key (user_id, date),
  check (cardinality(priorities) <= 3)
);
create index if not exists check_ins_user_updated_idx on public.daily_check_ins(user_id, updated_at);

create table if not exists public.reminder_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  preferences jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$ begin new.updated_at = greatest(now(), old.updated_at + interval '1 microsecond'); return new; end; $$;
drop trigger if exists tasks_set_updated_at on public.tasks;
create trigger tasks_set_updated_at before update on public.tasks for each row execute function public.set_updated_at();
drop trigger if exists check_ins_set_updated_at on public.daily_check_ins;
create trigger check_ins_set_updated_at before update on public.daily_check_ins for each row execute function public.set_updated_at();
drop trigger if exists profiles_set_updated_at on public.user_profiles;
create trigger profiles_set_updated_at before update on public.user_profiles for each row execute function public.set_updated_at();
drop trigger if exists reminder_preferences_set_updated_at on public.reminder_preferences;
create trigger reminder_preferences_set_updated_at before update on public.reminder_preferences for each row execute function public.set_updated_at();

do $$
declare t text;
begin
  foreach t in array array['user_profiles', 'tasks', 'daily_check_ins', 'reminder_preferences'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on table public.%I from anon, authenticated', t);
    execute format('grant select, insert, update on table public.%I to authenticated', t);
    execute format('drop policy if exists owner_select on public.%I', t);
    execute format('drop policy if exists owner_insert on public.%I', t);
    execute format('drop policy if exists owner_update on public.%I', t);
    execute format('create policy owner_select on public.%I for select to authenticated using ((select auth.uid()) is not null and user_id = (select auth.uid()))', t);
    execute format('create policy owner_insert on public.%I for insert to authenticated with check ((select auth.uid()) is not null and user_id = (select auth.uid()))', t);
    execute format('create policy owner_update on public.%I for update to authenticated using ((select auth.uid()) is not null and user_id = (select auth.uid())) with check ((select auth.uid()) is not null and user_id = (select auth.uid()))', t);
  end loop;
end $$;
