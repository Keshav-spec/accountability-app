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
}
export interface DailyCheckIn {
  date: string;
  morningPlan: string | null;
  eveningReflection: string | null;
  createdAt: string;
  updatedAt: string;
}
export function validateTaskTitle(title: string): string | null;
export function today(): string;
