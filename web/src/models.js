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
