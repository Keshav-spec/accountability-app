import { today, type Task, type DailyCheckIn } from './models.js';

export type ReminderSettings = {
  enabled: boolean; times: string[]; quietStart: string; quietEnd: string; dailyLimit: number; snoozeMinutes: number;
  stoppedDate: string | null; snoozeUntil: number | null; deliveredDate: string | null; deliveredCount: number; deliveredSlots: string[];
};
export type AppState = { tasks: Task[]; checkIns: Record<string, DailyCheckIn>; reminders: ReminderSettings; syncOwnerId?: string | null };

export function defaultState(includeExamples = true): AppState {
  const stamp = new Date().toISOString();
  const date = today();
  return {
    tasks: includeExamples ? [
      { id: 'sample-water', title: 'Drink a glass of water', notes: null, scheduledDate: date, targetTime: null, status: 'planned', createdAt: stamp, updatedAt: stamp },
      { id: 'sample-air', title: 'Step outside for a few minutes', notes: null, scheduledDate: date, targetTime: null, status: 'planned', createdAt: stamp, updatedAt: stamp },
    ] : [],
    checkIns: includeExamples ? { [date]: { date, morningPlan: null, priorities: [], progressNote: null, eveningReflection: null, dayOutcome: null, createdAt: stamp, updatedAt: stamp } } : {},
    reminders: { enabled: false, times: ['09:00', '13:00', '18:00'], quietStart: '22:00', quietEnd: '08:00', dailyLimit: 3, snoozeMinutes: 10, stoppedDate: null, snoozeUntil: null, deliveredDate: null, deliveredCount: 0, deliveredSlots: [] },
  };
}

function migrate(value: any): AppState {
  const defaults = defaultState();
  const oldCheckIn = value?.checkIn;
  const checkIns = value?.checkIns ?? (oldCheckIn ? { [oldCheckIn.date || today()]: { ...oldCheckIn, priorities: oldCheckIn.priorities ?? [], progressNote: null, dayOutcome: null } } : defaults.checkIns);
  return { ...defaults, ...value, checkIns, reminders: { ...defaults.reminders, ...value?.reminders }, syncOwnerId: value?.syncOwnerId ?? null } as AppState;
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
  private async read(db: IDBDatabase, key: string): Promise<AppState | undefined> {
    return new Promise((resolve, reject) => {
      const request = db.transaction('state', 'readonly').objectStore('state').get(key);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }
  private async write(db: IDBDatabase, key: string, value: AppState): Promise<void> {
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('state', 'readwrite'); tx.objectStore('state').put(value, key);
      tx.oncomplete = () => resolve(); tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error);
    });
  }
  async load(scope = 'guest'): Promise<AppState> {
    const db = await this.dbPromise;
    const key = scope === 'guest' ? 'guest' : `account:${scope}`;
    const scoped = await this.read(db, key);
    if (scoped) return migrate(scoped);
    const legacyMain = await this.read(db, 'main');
    if (legacyMain) {
      const legacy = migrate(legacyMain);
      const oldOwner = legacy.syncOwnerId;
      if (oldOwner) {
        await this.write(db, `account:${oldOwner}`, legacy);
        await new Promise<void>((resolve) => { const tx = db.transaction('state', 'readwrite'); tx.objectStore('state').delete('main'); tx.oncomplete = () => resolve(); tx.onerror = () => resolve(); });
        if (scope === oldOwner) return legacy;
      } else {
        // Claim unowned pre-account data for the first account that opens it, or retain it as guest data.
        await this.write(db, key, legacy);
        await new Promise<void>((resolve) => { const tx = db.transaction('state', 'readwrite'); tx.objectStore('state').delete('main'); tx.oncomplete = () => resolve(); tx.onerror = () => resolve(); });
        return legacy;
      }
    }
    let legacy: any = null;
    try { legacy = JSON.parse(localStorage.getItem('steady-stage1') ?? 'null'); } catch { /* Start with a clean local copy if the legacy entry is damaged. */ }
    const initial = migrate(legacy ?? defaultState(scope === 'guest'));
    await this.save(initial, scope);
    localStorage.removeItem('steady-stage1');
    return initial;
  }
  async save(state: AppState, scope = 'guest'): Promise<void> {
    const db = await this.dbPromise;
    await this.write(db, scope === 'guest' ? 'guest' : `account:${scope}`, state);
  }
}
