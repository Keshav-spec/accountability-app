export type TaskStatus = 'planned' | 'completed' | 'skipped';
export interface Task {
  id: string;
  title: string;
  notes: string | null;
  scheduledDate: string;
  targetTime: string | null;
  status: TaskStatus;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
  syncPending?: boolean;
}
export interface DailyCheckIn {
  date: string;
  morningPlan: string | null;
  priorities: string[];
  progressNote: string | null;
  eveningReflection: string | null;
  dayOutcome: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
  syncPending?: boolean;
}
export function validateTaskTitle(title: string): string | null;
export function today(date?: Date): string;
export function toggleComplete(task: Task): Task;
export function setTaskStatus(task: Task, status: TaskStatus): Task;
