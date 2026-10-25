import type { ReminderSettings } from './localRepository';

export type ReminderCandidate = { at: Date; slotKey: string };
export function isQuietAt(date: Date, start: string, end: string): boolean;
export function moveOutOfQuietHours(date: Date, start: string, end: string): Date;
export function nextReminder(now: Date, settings: ReminderSettings, hasPlannedTasks: boolean): ReminderCandidate | null;
export function mayDeliver(now: Date, settings: ReminderSettings, slotKey: string, hasPlannedTasks: boolean): boolean;
export function recordDelivery(settings: ReminderSettings, now: Date, slotKey: string): ReminderSettings;
