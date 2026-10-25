import type { DailyCheckIn, Task } from './models.js';
export function resolveConflict<T extends { updatedAt: string }>(local: T, remote: T): { value: T; action: 'use-remote' | 'upload-local' };
export function taskToRow(task: Task, userId: string): Record<string, unknown>;
export function taskFromRow(row: Record<string, any>): Task;
export function checkInToRow(item: DailyCheckIn, userId: string): Record<string, unknown>;
export function checkInFromRow(row: Record<string, any>): DailyCheckIn;
export function shouldQueueUpload(local: { updatedAt: string }, remote?: { updated_at: string } | null): boolean;
export function mergeRemote<T extends { updatedAt: string; syncPending?: boolean }>(local: T | null, remote: T): T;
