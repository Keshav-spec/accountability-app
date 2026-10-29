export const today = (date = new Date()) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
};

export function validateTaskTitle(title) {
  if (!title.trim()) return 'Add a task title to continue.';
  if (title.length > 120) return 'Keep the title to 120 characters or fewer.';
  return null;
}

export function toggleComplete(task) {
  return { ...task, status: task.status === 'completed' ? 'planned' : 'completed', updatedAt: new Date().toISOString() };
}

export function setTaskStatus(task, status) {
  return { ...task, status, updatedAt: new Date().toISOString() };
}

export function nextRecurringDate(date, rule) {
  if (rule === 'none' || !rule) return null;
  const next = new Date(`${date}T12:00:00`);
  if (rule === 'daily') next.setDate(next.getDate() + 1);
  else if (rule === 'weekly') next.setDate(next.getDate() + 7);
  else if (rule === 'weekdays') {
    do { next.setDate(next.getDate() + 1); } while (next.getDay() === 0 || next.getDay() === 6);
  }
  return today(next);
}

export function weekStart(date = new Date()) {
  const start = new Date(date); start.setHours(12, 0, 0, 0);
  start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
  return today(start);
}
