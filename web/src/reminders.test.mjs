import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isQuietAt, mayDeliver, nextReminder, recordDelivery } from './reminders.js';

const settings = (overrides = {}) => ({
  enabled: true, times: ['09:00'], quietStart: '22:00', quietEnd: '08:00', dailyLimit: 3, snoozeMinutes: 10,
  stoppedDate: null, snoozeUntil: null, deliveredDate: null, deliveredCount: 0, deliveredSlots: [], ...overrides,
});

test('quiet hours cover the midnight boundary and defer to the morning', () => {
  const now = new Date(2026, 9, 25, 5, 0);
  assert.equal(isQuietAt(now, '22:00', '08:00'), true);
  assert.equal(nextReminder(now, settings({ times: ['06:00'] }), true).at.getHours(), 8);
});

test('reminder limit and stop-for-day suppress today but resume tomorrow', () => {
  const now = new Date(2026, 9, 25, 7, 0);
  const capped = nextReminder(now, settings({ deliveredDate: '2026-10-25', deliveredCount: 3 }), true);
  const stopped = nextReminder(now, settings({ stoppedDate: '2026-10-25' }), true);
  assert.equal(capped.at.getDate(), 26);
  assert.equal(stopped.at.getDate(), 26);
});

test('no planned tasks means no reminders', () => {
  assert.equal(nextReminder(new Date(2026, 9, 25, 7), settings(), false), null);
  assert.equal(mayDeliver(new Date(2026, 9, 25, 9), settings(), 'slot', false), false);
});

test('a persisted slot key prevents duplicates and daily counts persist', () => {
  const now = new Date(2026, 9, 25, 9, 0);
  const slot = '2026-10-25_09:00';
  const delivered = recordDelivery(settings(), now, slot);
  assert.equal(delivered.deliveredCount, 1);
  assert.equal(mayDeliver(now, delivered, slot, true), false);
});

test('snooze becomes the next eligible reminder', () => {
  const now = new Date(2026, 9, 25, 9, 30);
  const snoozeAt = new Date(2026, 9, 25, 9, 40).getTime();
  const next = nextReminder(now, settings({ snoozeUntil: snoozeAt }), true);
  assert.equal(next.slotKey, `snooze:${snoozeAt}`);
});
