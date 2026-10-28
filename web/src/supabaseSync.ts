import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import type { AppState } from './localRepository';
import { checkInFromRow, checkInToRow, mergeRemote, shouldQueueUpload, taskFromRow, taskToRow } from './syncRules.js';

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string | undefined;
export const supabase: SupabaseClient | null = url && key ? createClient(url, key) : null;
export type SyncStatus = 'synced' | 'syncing' | 'offline' | 'needs attention';
let latestSyncAt: string | null = localStorage.getItem('steady-last-sync');
export function getLastSyncAt(): string | null { return latestSyncAt; }

export async function requestGoogleSignIn(): Promise<void> {
  if (!supabase) throw new Error('Supabase is not configured.');
  const { error } = await supabase.auth.signInWithOAuth({ provider: 'google', options: { redirectTo: window.location.origin } });
  if (error) throw error;
}

export async function loadProfileName(): Promise<string | null> {
  if (!supabase) return null;
  const { data: { user }, error: authError } = await supabase.auth.getUser();
  if (authError || !user) return null;
  const { data, error } = await supabase.from('user_profiles').select('display_name').eq('user_id', user.id).maybeSingle();
  if (error) throw error;
  const storedName = typeof data?.display_name === 'string' ? data.display_name.trim() : '';
  const authName = typeof user.user_metadata?.display_name === 'string' ? user.user_metadata.display_name.trim() : '';
  return storedName || authName || null;
}

export async function saveProfileName(name: string): Promise<void> {
  if (!supabase) throw new Error('Supabase is not configured.');
  const normalized = name.trim();
  if (!normalized || normalized.length > 80) throw new Error('Enter a name between 1 and 80 characters.');
  const { data: { user }, error: authError } = await supabase.auth.getUser();
  if (authError || !user) throw new Error('Sign in again to update your profile name.');
  const { error } = await supabase.from('user_profiles').upsert(
    { user_id: user.id, display_name: normalized },
    { onConflict: 'user_id' },
  );
  if (error) throw error;
}

export async function syncState(state: AppState, save: (next: AppState) => Promise<void>): Promise<SyncStatus> {
  if (!supabase) return 'needs attention';
  if (!navigator.onLine) return 'offline';
  const { data: { user }, error: authError } = await supabase.auth.getUser();
  if (authError || !user) return 'needs attention';
  if (state.syncOwnerId && state.syncOwnerId !== user.id) return 'needs attention';
  state.syncOwnerId = user.id;
  await save(state);

  const [tasksResult, checkInsResult] = await Promise.all([
    supabase.from('tasks').select('*').eq('user_id', user.id).order('updated_at', { ascending: true }),
    supabase.from('daily_check_ins').select('*').eq('user_id', user.id).order('updated_at', { ascending: true }),
  ]);
  if (tasksResult.error || checkInsResult.error) throw tasksResult.error ?? checkInsResult.error;

  for (const row of tasksResult.data ?? []) {
    const remote = taskFromRow(row); const index = state.tasks.findIndex((item) => item.id === remote.id);
    state.tasks[index < 0 ? state.tasks.length : index] = mergeRemote(index < 0 ? null : state.tasks[index], remote);
  }
  for (const row of checkInsResult.data ?? []) {
    const remote = checkInFromRow(row); const local = state.checkIns[remote.date];
    state.checkIns[remote.date] = mergeRemote(local ?? null, remote);
  }

  // Claim unsynced local records on first sign-in; only this account's rows are ever queried or written.
  for (const task of state.tasks) {
    const row = (tasksResult.data ?? []).find((item) => item.id === task.id);
    if (task.syncPending || !row) {
      const { error } = await supabase.from('tasks').upsert(taskToRow(task, user.id), { onConflict: 'id' });
      if (error) throw error;
      task.syncPending = false;
    }
  }
  for (const item of Object.values(state.checkIns)) {
    const row = (checkInsResult.data ?? []).find((value) => value.date === item.date);
    if (item.syncPending || !row) {
      const { error } = await supabase.from('daily_check_ins').upsert(checkInToRow(item, user.id), { onConflict: 'user_id,date' });
      if (error) throw error;
      item.syncPending = false;
    }
  }
  await save(state);
  latestSyncAt = new Date().toISOString();
  localStorage.setItem('steady-last-sync', latestSyncAt);
  return 'synced';
}
