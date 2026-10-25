import test from 'node:test';
import assert from 'node:assert/strict';
import { checkInFromRow, checkInToRow, mergeRemote, shouldQueueUpload, taskFromRow, taskToRow } from './syncRules.js';

const task = { id: '00000000-0000-4000-8000-000000000001', title: 'Walk', notes: 'quietly', scheduledDate: '2026-10-25', targetTime: '09:00', status: 'planned', createdAt: '2026-10-20T00:00:00Z', updatedAt: '2026-10-20T01:00:00Z', deletedAt: null, syncPending: true };
test('task sync mapping preserves fields and deletion tombstones', () => {
  const row = taskToRow({ ...task, deletedAt: '2026-10-21T00:00:00Z' }, 'user-a');
  assert.equal(row.user_id, 'user-a'); assert.equal(row.scheduled_date, task.scheduledDate); assert.equal(row.deleted_at, '2026-10-21T00:00:00Z');
  const mapped = taskFromRow(row); assert.equal(mapped.scheduledDate, task.scheduledDate); assert.equal(mapped.deletedAt, row.deleted_at); assert.equal(mapped.syncPending, false);
});
test('check-in sync mapping preserves priorities, reflection, and ownership', () => {
  const item = { date: '2026-10-25', priorities: ['write'], morningPlan: 'steady', progressNote: 'started', eveningReflection: 'good', dayOutcome: 'REST', createdAt: task.createdAt, updatedAt: task.updatedAt };
  const row = checkInToRow(item, 'user-a'); assert.equal(row.user_id, 'user-a'); assert.deepEqual(checkInFromRow(row).priorities, ['write']);
  assert.equal(checkInFromRow(row).eveningReflection, 'good');
});
test('newer timestamp wins and remote wins exact timestamp ties', () => {
  assert.equal(mergeRemote({ ...task, title: 'local' }, { ...task, title: 'remote', updatedAt: '2026-10-20T02:00:00Z' }).title, 'remote');
  assert.equal(mergeRemote({ ...task, title: 'local' }, { ...task, title: 'remote' }).title, 'remote');
  assert.equal(mergeRemote({ ...task, title: 'local' }, { ...task, title: 'remote', updatedAt: '2026-10-19T00:00:00Z' }).syncPending, true);
});
test('offline/local edits remain queued until newer remote state is known', () => {
  assert.equal(shouldQueueUpload({ ...task, syncPending: true }, null), true);
  assert.equal(shouldQueueUpload({ ...task, updatedAt: '2026-10-21T00:00:00Z' }, { updated_at: task.updatedAt }), true);
  assert.equal(shouldQueueUpload(task, { updated_at: '2026-10-21T00:00:00Z' }), false);
});
test('deletions are synchronized as retained tombstone records', () => {
  const deleted = { ...task, deletedAt: '2026-10-21T00:00:00Z', updatedAt: '2026-10-21T00:00:00Z' };
  const merged = mergeRemote(task, taskFromRow(taskToRow(deleted, 'user-a')));
  assert.equal(merged.deletedAt, deleted.deletedAt); assert.equal(merged.syncPending, false);
});
