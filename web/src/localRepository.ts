import { today, type Task, type DailyCheckIn } from './models.js';

export type ReminderSettings = {
  enabled: boolean; times: string[]; quietStart: string; quietEnd: string; dailyLimit: number; snoozeMinutes: number;
  stoppedDate: string | null; snoozeUntil: number | null; deliveredDate: string | null; deliveredCount: number; deliveredSlots: string[];
};
export type AppState = { tasks: Task[]; checkIns: Record<string, DailyCheckIn>; reminders: ReminderSettings };

export function defaultState(): AppState {
  const stamp = new Date().toISOString();
  const date = today();
  return {
    tasks: [
      { id: 'sample-water', title: 'Drink a glass of water', notes: null, scheduledDate: date, targetTime: null, status: 'planned', createdAt: stamp, updatedAt: stamp },
      { id: 'sample-air', title: 'Step outside for a few minutes', notes: null, scheduledDate: date, targetTime: null, status: 'planned', createdAt: stamp, updatedAt: stamp },
    ],
    checkIns: { [date]: { date, morningPlan: null, priorities: [], progressNote: null, eveningReflection: null, dayOutcome: null, createdAt: stamp, updatedAt: stamp } },
    reminders: { enabled: false, times: ['09:00', '13:00', '18:00'], quietStart: '22:00', quietEnd: '08:00', dailyLimit: 3, snoozeMinutes: 10, stoppedDate: null, snoozeUntil: null, deliveredDate: null, deliveredCount: 0, deliveredSlots: [] },
  };
}

function migrate(value: any): AppState {
  const defaults = defaultState();
  const oldCheckIn = value?.checkIn;
  const checkIns = value?.checkIns ?? (oldCheckIn ? { [oldCheckIn.date || today()]: { ...oldCheckIn, priorities: oldCheckIn.priorities ?? [], progressNote: null, dayOutcome: null } } : defaults.checkIns);
  return { ...defaults, ...value, checkIns, reminders: { ...defaults.reminders, ...value?.reminders } } as AppState;
}

export class LocalRepository {
  private dbPromise: Promise<IDBDatabase>;
  constructor() {
    this.dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open('steady-local', 1);
      request.onupgradeneeded = () => request.result.createObjectStore('state');
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }
  async load(): Promise<AppState> {
    const db = await this.dbPromise;
    const stored = await new Promise<AppState | undefined>((resolve, reject) => {
      const request = db.transaction('state', 'readonly').objectStore('state').get('main');
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    if (stored) return migrate(stored);
    let legacy: any = null;
    try { legacy = JSON.parse(localStorage.getItem('steady-stage1') ?? 'null'); } catch { /* Start with a clean local copy if the legacy entry is damaged. */ }
    const initial = migrate(legacy ?? defaultState());
    await this.save(initial);
    localStorage.removeItem('steady-stage1');
    return initial;
  }
  async save(state: AppState): Promise<void> {
    const db = await this.dbPromise;
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction('state', 'readwrite');
      transaction.objectStore('state').put(state, 'main');
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
  }
}
