// Latest updated_at wins. Remote wins exact timestamp ties so devices converge deterministically.
export function resolveConflict(local, remote) {
  return Date.parse(remote.updatedAt) >= Date.parse(local.updatedAt)
    ? { value: remote, action: 'use-remote' }
    : { value: local, action: 'upload-local' };
}

export function taskToRow(task, userId) {
  return { id: task.id, title: task.title, notes: task.notes, scheduled_date: task.scheduledDate,
    target_time: task.targetTime, status: task.status, created_at: task.createdAt,
    updated_at: task.updatedAt, deleted_at: task.deletedAt ?? null, user_id: userId };
}
export function taskFromRow(row) {
  return { id: row.id, title: row.title, notes: row.notes, scheduledDate: row.scheduled_date, targetTime: row.target_time?.slice(0, 5) ?? null,
    status: row.status, createdAt: row.created_at, updatedAt: row.updated_at, deletedAt: row.deleted_at, syncPending: false };
}
export function checkInToRow(item, userId) {
  return { user_id: userId, date: item.date, morning_plan: item.morningPlan, priorities: item.priorities,
    progress_note: item.progressNote, evening_reflection: item.eveningReflection, day_outcome: item.dayOutcome,
    created_at: item.createdAt, updated_at: item.updatedAt, deleted_at: item.deletedAt ?? null };
}
export function checkInFromRow(row) {
  return { date: row.date, morningPlan: row.morning_plan, priorities: row.priorities ?? [], progressNote: row.progress_note,
    eveningReflection: row.evening_reflection, dayOutcome: row.day_outcome, createdAt: row.created_at,
    updatedAt: row.updated_at, deletedAt: row.deleted_at, syncPending: false };
}

export function shouldQueueUpload(local, remote) {
  return !remote || Date.parse(local.updatedAt) > Date.parse(remote.updated_at);
}
export function mergeRemote(local, remote) {
  if (!local) return { ...remote, syncPending: false };
  return Date.parse(remote.updatedAt) >= Date.parse(local.updatedAt)
    ? { ...remote, syncPending: false }
    : { ...local, syncPending: true };
}
