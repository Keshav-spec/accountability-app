import './style.css';
import './stage2.css';
import './stage3.css';
import { today, setTaskStatus, validateTaskTitle, type Task, type DailyCheckIn } from './models.js';
import { mayDeliver, nextReminder, recordDelivery } from './reminders.js';
import { defaultState, LocalRepository, type AppState } from './localRepository';
import { supabase, syncState, type SyncStatus } from './supabaseSync.js';

const repo = new LocalRepository();
let state: AppState = defaultState();
let page: 'Today' | 'Tasks' | 'Check-in' | 'Reminders' | 'Account' = 'Today';
let editingId: string | null = null;
let editorOpen = false;
let webRegistration: ServiceWorkerRegistration | null = null;
let timer: ReturnType<typeof setTimeout> | null = null;
let statusMessage = '';
let syncStatus: SyncStatus = navigator.onLine ? 'needs attention' : 'offline';
let accountEmail = '';
let syncActive = false;
let syncQueued = false;

const escapeHtml = (value: string) => value.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
const checkInForToday = (): DailyCheckIn => state.checkIns[today()] ?? { date: today(), morningPlan: null, priorities: [], progressNote: null, eveningReflection: null, dayOutcome: null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
const pendingToday = () => state.tasks.some((task) => !task.deletedAt && task.scheduledDate === today() && task.status === 'planned');

async function persist(): Promise<void> { await repo.save(state); scheduleReminder(); if (supabase && accountEmail) void runSync(); }

async function runSync(): Promise<void> {
  if (!supabase || !accountEmail) return;
  if (syncActive) { syncQueued = true; return; }
  syncActive = true;
  do {
    syncQueued = false; syncStatus = 'syncing'; render();
    try { syncStatus = await syncState(state, (next) => repo.save(next)); }
    catch { syncStatus = navigator.onLine ? 'needs attention' : 'offline'; }
  } while (syncQueued && navigator.onLine);
  syncActive = false; render();
}

async function accountAction(action: 'signup' | 'signin'): Promise<void> {
  if (!supabase) return;
  const form = document.querySelector<HTMLFormElement>('#account-form'); if (!form) return;
  const data = new FormData(form); const email = String(data.get('email') ?? '').trim(); const password = String(data.get('password') ?? '');
  const result = action === 'signup' ? await supabase.auth.signUp({ email, password }) : await supabase.auth.signInWithPassword({ email, password });
  if (result.error) { statusMessage = result.error.message; render(); return; }
  accountEmail = result.data.user?.email ?? email;
  statusMessage = result.data.session ? 'Signed in. Syncing your local plan.' : 'Check your email to confirm your account, then sign in.';
  render(); if (result.data.session) await runSync();
}

async function signOut(): Promise<void> { await supabase?.auth.signOut(); accountEmail = ''; syncStatus = navigator.onLine ? 'offline' : 'offline'; render(); }

function render(): void {
  const app = document.querySelector<HTMLDivElement>('#app')!;
  const tabs = ['Today', 'Tasks', 'Check-in', 'Reminders', 'Account'] as const;
  const icon: Record<(typeof tabs)[number], string> = { Today: '◷', Tasks: '✓', 'Check-in': '♡', Reminders: '◌', Account: '◎' };
  const pageContent = page === 'Today' ? todayPage() : page === 'Tasks' ? tasksPage() : page === 'Check-in' ? checkInPage() : page === 'Reminders' ? remindersPage() : accountPanel();
  app.innerHTML = `<div class="layout"><aside class="rail"><a class="brand" href="#"><span class="brand-mark">s</span><span>steady</span></a><div class="rail-label">YOUR SPACE</div><nav>${tabs.map((tab) => `<button class="nav-link ${page === tab ? 'active' : ''}" data-page="${tab}"><span class="nav-dot">${icon[tab]}</span>${tab}</button>`).join('')}</nav><div class="rail-note"><span class="sun">☼</span><p>One step at a time is enough.</p></div></aside><main><header><div class="eyebrow">YOUR SPACE <span>·</span> ${new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).toUpperCase()}</div><div class="account-strip"><span class="sync-state">${syncStatus}</span>${accountEmail ? `<span>${escapeHtml(accountEmail)}</span><button class="small-button" id="sync-now">Sync now</button><button class="small-button" id="signout">Sign out</button>` : '<button class="small-button" id="account-toggle">Account</button>'}</div><div class="mobile-nav">${tabs.map((tab) => `<button class="nav-link ${page === tab ? 'active' : ''}" data-page="${tab}">${tab}</button>`).join('')}</div></header><section class="content">${pageContent}${statusMessage ? `<p class="status-message" role="status">${escapeHtml(statusMessage)}</p>` : ''}<footer>Your plan can change as the day unfolds.</footer></section></main></div>`;
  app.querySelectorAll<HTMLButtonElement>('[data-page]').forEach((button) => button.addEventListener('click', () => { page = button.dataset.page as typeof page; editingId = null; editorOpen = false; render(); }));
  app.querySelectorAll<HTMLButtonElement>('[data-status]').forEach((button) => button.addEventListener('click', () => changeStatus(button.dataset.id!, button.dataset.status as Task['status'])));
  app.querySelectorAll<HTMLButtonElement>('[data-delete]').forEach((button) => button.addEventListener('click', () => void deleteTask(button.dataset.delete!)));
  app.querySelectorAll<HTMLButtonElement>('[data-edit]').forEach((button) => button.addEventListener('click', () => { editingId = button.dataset.edit === 'new' ? null : button.dataset.edit!; editorOpen = true; render(); }));
  app.querySelector<HTMLFormElement>('#task-form')?.addEventListener('submit', saveTask);
  app.querySelector<HTMLButtonElement>('#cancel-edit')?.addEventListener('click', () => { editingId = null; editorOpen = false; render(); });
  app.querySelector<HTMLFormElement>('#checkin-form')?.addEventListener('submit', saveCheckIn);
  app.querySelector<HTMLFormElement>('#reminder-form')?.addEventListener('submit', saveReminderSettings);
  app.querySelector<HTMLButtonElement>('#account-toggle')?.addEventListener('click', () => { page = 'Account'; render(); });
  app.querySelector<HTMLButtonElement>('#signout')?.addEventListener('click', () => void signOut());
  app.querySelector<HTMLButtonElement>('#sync-now')?.addEventListener('click', () => void runSync());
  app.querySelector<HTMLButtonElement>('#signin')?.addEventListener('click', () => void accountAction('signin'));
  app.querySelector<HTMLButtonElement>('#signup')?.addEventListener('click', () => void accountAction('signup'));
  app.querySelector<HTMLButtonElement>('#enable-browser-reminders')?.addEventListener('click', enableBrowserReminders);
  app.querySelector<HTMLButtonElement>('#stop-today')?.addEventListener('click', stopRemindersToday);
  app.querySelector<HTMLButtonElement>('#disable-reminders')?.addEventListener('click', () => void disableReminders());
}

function accountPanel(): string {
  const intro = `<div class="page-intro"><p class="eyebrow">OPTIONAL ACCOUNT</p><h1>Account and sync</h1><p class="subhead">Your tasks stay on this device and sync when you sign in online.</p></div>`;
  if (!supabase) return `${intro}<article class="check-card">Supabase is not configured. Local planning still works. Add the project URL and publishable key to web/.env.local to enable accounts.</article>`;
  if (accountEmail) return `${intro}<article class="check-card"><h2>You’re signed in</h2><p>${escapeHtml(accountEmail)}</p><p>Sync status: ${escapeHtml(syncStatus)}</p><div class="form-actions"><button class="primary-button" id="sync-now">Sync now</button><button class="small-button" id="signout">Sign out</button></div></article>`;
  return `${intro}${syncStatus === 'needs attention' ? '<article class="check-card">Sync needs attention. Check your connection, account, and Supabase setup. Your local edits remain saved here.</article>' : ''}<form id="account-form" class="check-card"><label>Email<input name="email" type="email" autocomplete="email" required></label><label>Password<input name="password" type="password" autocomplete="new-password" minlength="8" required></label><div class="form-actions"><button class="primary-button" id="signin" type="button">Sign in</button><button class="small-button" id="signup" type="button">Create account</button></div></form>`;
}

function todayPage(): string {
  const date = today();
  const active = state.tasks.filter((task) => !task.deletedAt && task.scheduledDate === date && task.status === 'planned');
  const done = state.tasks.filter((task) => !task.deletedAt && task.scheduledDate === date && task.status !== 'planned');
  const upcoming = state.tasks.filter((task) => !task.deletedAt && task.scheduledDate > date && task.status === 'planned').sort((a, b) => a.scheduledDate.localeCompare(b.scheduledDate));
  return `<div class="welcome"><div class="welcome-copy"><p class="eyebrow">A GENTLER WAY TO GET THINGS DONE</p><h1>A little structure,<br>with room to breathe.</h1><p class="subhead">Small steps count. So does taking a pause.</p></div><div class="orbit" aria-hidden="true"><span>✳</span></div></div><article class="plan-card"><div class="card-heading"><div><p class="eyebrow">TODAY</p><h2>Your gentle plan</h2></div><div class="progress"><strong>${done.filter((task) => task.status === 'completed').length}</strong> / ${state.tasks.filter((task) => task.scheduledDate === date).length}</div></div>${taskList(active)}<button class="text-button" data-edit="new">＋ &nbsp; Add a small task</button>${done.length ? `<h3 class="section-label">DONE OR SKIPPED TODAY</h3>${taskList(done)}` : ''}</article>${upcoming.length ? `<article class="plan-card secondary-card"><p class="eyebrow">COMING UP</p><h2>When you’re ready</h2>${taskList(upcoming)}</article>` : ''}<p class="soft-prompt"><span>✦</span> You don’t have to do it all. Just begin where you are.</p>${editorOpen ? taskEditor() : ''}`;
}

function tasksPage(): string {
  const date = today();
  const upcoming = state.tasks.filter((task) => !task.deletedAt && task.scheduledDate >= date && task.status === 'planned').sort((a, b) => a.scheduledDate.localeCompare(b.scheduledDate));
  const history = state.tasks.filter((task) => !task.deletedAt && task.scheduledDate === date && task.status !== 'planned');
  return `<div class="page-intro"><p class="eyebrow">YOUR LIST</p><h1>Tasks</h1><p class="subhead">Change the plan whenever you need.</p></div><button class="primary-button" data-edit="new">＋ &nbsp; Add a task</button><article class="plan-card task-manager"><h2>Today and coming up</h2>${taskList(upcoming, true)}</article>${history.length ? `<article class="plan-card task-manager"><h2>Completed and skipped today</h2>${taskList(history, true)}</article>` : ''}${editorOpen ? taskEditor() : ''}`;
}

function taskList(tasks: Task[], showDate = false): string {
  if (!tasks.length) return '<p class="empty">Nothing on this part of your list yet.</p>';
  return `<div class="task-list">${tasks.map((task) => `<div class="task-row"><div class="task-main"><span class="task-title ${task.status === 'completed' ? 'done' : ''}">${escapeHtml(task.title)}</span><span class="task-meta">${showDate ? escapeHtml(task.scheduledDate) : ''}${task.targetTime ? ` · ${escapeHtml(task.targetTime)}` : ''}${task.status !== 'planned' ? ` · ${escapeHtml(task.status)}` : ''}</span>${task.notes ? `<span class="task-notes">${escapeHtml(task.notes)}</span>` : ''}</div><div class="task-actions"><button class="small-button" data-edit="${escapeHtml(task.id)}">Edit / move</button>${task.status === 'planned' ? `<button class="small-button" data-id="${escapeHtml(task.id)}" data-status="completed">Complete</button><button class="small-button subtle-button" data-id="${escapeHtml(task.id)}" data-status="skipped">Skip</button>` : `<button class="small-button subtle-button" data-id="${escapeHtml(task.id)}" data-status="planned">Undo</button>`}<button class="small-button subtle-button" data-delete="${escapeHtml(task.id)}">Delete</button></div></div>`).join('')}</div>`;
}

function taskEditor(): string {
  const task = state.tasks.find((item) => item.id === editingId);
  const when = task?.scheduledDate ?? today();
  return `<form id="task-form" class="edit-card"><h2>${task ? 'Edit task' : 'Add a small task'}</h2><label>Title<input name="title" maxlength="120" required value="${escapeHtml(task?.title ?? '')}" placeholder="One small thing"></label><label>Notes (optional)<textarea name="notes" rows="2">${escapeHtml(task?.notes ?? '')}</textarea></label><div class="form-columns"><label>Date<input name="date" type="date" required value="${escapeHtml(when)}"></label><label>Target time<input name="time" type="time" value="${escapeHtml(task?.targetTime ?? '')}"></label></div><p class="form-error" id="task-error" role="alert"></p><div class="form-actions"><button class="primary-button" type="submit">Save task</button><button class="small-button" id="cancel-edit" type="button">Cancel</button></div></form>`;
}

function checkInPage(): string {
  const checkIn = checkInForToday();
  const priorityInputs = [0, 1, 2].map((index) => `<label>Priority ${index + 1}<input name="priority${index}" maxlength="120" value="${escapeHtml(checkIn.priorities[index] ?? '')}" placeholder="A small, meaningful step"></label>`).join('');
  const choices = [['COMPLETE', 'Complete'], ['MIXED', 'Mixed'], ['REST', 'Rest']].map(([key, label]) => `<label class="choice"><input type="radio" name="outcome" value="${key}" ${checkIn.dayOutcome === key ? 'checked' : ''}>${label}</label>`).join('');
  return `<div class="page-intro"><p class="eyebrow">A MOMENT FOR YOU</p><h1>Check in with yourself</h1><p class="subhead">No right answers. Just a little space to notice.</p></div><form id="checkin-form"><article class="check-card"><h2>Morning plan</h2><p>Choose up to three gentle priorities.</p>${priorityInputs}<label>A short intention (optional)<textarea name="intention" rows="2" maxlength="240" placeholder="How would you like to meet the day?">${escapeHtml(checkIn.morningPlan ?? '')}</textarea></label></article><article class="check-card"><h2>Progress check-in</h2><p>How is it going so far?</p><div class="choice-row"><label class="choice"><input type="radio" name="progress" value="started" ${checkIn.progressNote?.startsWith('Started') ? 'checked' : ''}>I started</label><label class="choice"><input type="radio" name="progress" value="smaller" ${checkIn.progressNote?.startsWith('Smaller') ? 'checked' : ''}>Help me choose a smaller step</label></div><label>A smaller next step (optional)<textarea name="progressNote" rows="2" placeholder="What might feel manageable?">${escapeHtml(checkIn.progressNote?.replace(/^(Started|Smaller):\s*/, '') ?? '')}</textarea></label></article><article class="check-card"><h2>Evening review</h2><label>What would you like to remember?<textarea name="reflection" rows="3" placeholder="Something I’m glad I did…">${escapeHtml(checkIn.eveningReflection ?? '')}</textarea></label><p>Rest is a valid way to spend a day.</p><div class="choice-row">${choices}</div></article><button class="primary-button" type="submit">Save today’s check-in</button></form>`;
}

function remindersPage(): string {
  const settings = state.reminders;
  const supported = 'Notification' in window && 'serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost');
  const permText = !supported ? 'This browser or connection does not support local web notifications here.' : Notification.permission === 'granted' ? 'Browser notifications are allowed for this site.' : Notification.permission === 'denied' ? 'Notifications are blocked in browser settings. Change site permissions to opt in.' : 'Notifications are off until you choose to allow them.';
  return `<div class="page-intro"><p class="eyebrow">GENTLE NUDGES</p><h1>Reminder settings</h1><p class="subhead">Choose a rhythm that feels helpful. You can change it any time.</p></div><article class="check-card reminder-card"><p>Web reminders can only run while this app is open. Browser and operating system support varies. Notification text stays on your device.</p><p class="permission-note">${escapeHtml(permText)}</p>${settings.enabled ? '<p class="enabled-note">Reminders are on.</p>' : `<button class="primary-button" id="enable-browser-reminders" ${supported ? '' : 'disabled'}>Turn on browser reminders</button>`}<form id="reminder-form"><label>Reminder times (comma-separated, 24-hour)<input name="times" value="${escapeHtml(settings.times.join(', '))}" placeholder="09:00, 13:00, 18:00"><small>For example: 09:00, 13:00, 18:00</small></label><div class="form-columns"><label>Quiet hours start<input type="time" name="quietStart" value="${escapeHtml(settings.quietStart)}"></label><label>Quiet hours end<input type="time" name="quietEnd" value="${escapeHtml(settings.quietEnd)}"></label></div><div class="form-columns"><label>Daily reminder limit<input type="number" name="dailyLimit" min="1" max="12" value="${settings.dailyLimit}"></label><label>Snooze minutes<input type="number" name="snoozeMinutes" min="5" max="120" value="${settings.snoozeMinutes}"></label></div><p class="form-error" id="reminder-error" role="alert"></p><button class="primary-button" type="submit">Save schedule</button><button class="small-button" id="stop-today" type="button">Stop reminders for today</button>${settings.enabled ? '<button class="small-button subtle-button" id="disable-reminders" type="button">Turn reminders off</button>' : ''}</form></article>`;
}

async function saveTask(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const form = event.currentTarget as HTMLFormElement; const data = new FormData(form);
  const title = String(data.get('title') ?? ''); const error = validateTaskTitle(title);
  if (error) { form.querySelector('#task-error')!.textContent = error; return; }
  const date = String(data.get('date')); const time = String(data.get('time'));
  if (time && !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) { form.querySelector('#task-error')!.textContent = 'Use a 24-hour target time.'; return; }
  const old = state.tasks.find((task) => task.id === editingId); const stamp = new Date().toISOString();
  const task: Task = { id: old?.id ?? crypto.randomUUID(), title: title.trim(), notes: String(data.get('notes') ?? '').trim() || null, scheduledDate: date, targetTime: time || null, status: old?.status ?? 'planned', createdAt: old?.createdAt ?? stamp, updatedAt: stamp, syncPending: true, deletedAt: null };
  if (old) state.tasks = state.tasks.map((item) => item.id === old.id ? task : item); else state.tasks.push(task);
  editingId = null; editorOpen = false; statusMessage = 'Task saved on this device.'; await persist(); render();
}

async function changeStatus(id: string, status: Task['status']): Promise<void> {
  state.tasks = state.tasks.map((task) => task.id === id ? { ...setTaskStatus(task, status), syncPending: true } : task);
  statusMessage = status === 'completed' ? 'Marked complete. You can undo this any time.' : status === 'skipped' ? 'Skipped for now. That’s okay.' : 'Task moved back to your plan.';
  await persist(); render();
}

async function deleteTask(id: string): Promise<void> {
  const stamp = new Date().toISOString();
  state.tasks = state.tasks.map((task) => task.id === id ? { ...task, deletedAt: stamp, updatedAt: stamp, syncPending: true } : task);
  statusMessage = 'Task deleted. It will stay deleted across your devices.'; await persist(); render();
}

async function saveCheckIn(event: SubmitEvent): Promise<void> {
  event.preventDefault(); const form = event.currentTarget as HTMLFormElement; const data = new FormData(form); const date = today();
  const priorities = [0, 1, 2].map((index) => String(data.get(`priority${index}`) ?? '').trim()).filter(Boolean).slice(0, 3);
  const progressType = String(data.get('progress') ?? ''); const note = String(data.get('progressNote') ?? '').trim();
  const progressNote = progressType === 'started' ? `Started${note ? `: ${note}` : ''}` : progressType === 'smaller' ? `Smaller${note ? `: ${note}` : ''}` : note || null;
  const existing = state.checkIns[date]; const stamp = new Date().toISOString();
  state.checkIns[date] = { date, priorities, morningPlan: String(data.get('intention') ?? '').trim() || null, progressNote, eveningReflection: String(data.get('reflection') ?? '').trim() || null, dayOutcome: String(data.get('outcome') ?? '') || null, createdAt: existing?.createdAt ?? stamp, updatedAt: stamp, syncPending: true };
  statusMessage = 'Check-in saved on this device.'; await persist(); render();
}

async function saveReminderSettings(event: SubmitEvent): Promise<void> {
  event.preventDefault(); const form = event.currentTarget as HTMLFormElement; const data = new FormData(form);
  const times = String(data.get('times')).split(',').map((value) => value.trim()).filter(Boolean);
  const dailyLimit = Number(data.get('dailyLimit')); const snoozeMinutes = Number(data.get('snoozeMinutes'));
  const validTime = (value: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(value);
  const error = !times.length || times.some((time) => !validTime(time)) ? 'Enter reminder times as HH:MM, separated with commas.' : !validTime(String(data.get('quietStart'))) || !validTime(String(data.get('quietEnd'))) ? 'Enter quiet hours as HH:MM.' : !Number.isInteger(dailyLimit) || dailyLimit < 1 || dailyLimit > 12 ? 'Daily limit must be between 1 and 12.' : !Number.isInteger(snoozeMinutes) || snoozeMinutes < 5 || snoozeMinutes > 120 ? 'Snooze must be between 5 and 120 minutes.' : '';
  form.querySelector('#reminder-error')!.textContent = error; if (error) return;
  state.reminders = { ...state.reminders, times: [...new Set(times)], quietStart: String(data.get('quietStart')), quietEnd: String(data.get('quietEnd')), dailyLimit, snoozeMinutes };
  statusMessage = 'Reminder schedule saved.'; await persist(); render();
}

async function enableBrowserReminders(): Promise<void> {
  if (!('Notification' in window) || !('serviceWorker' in navigator)) return;
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') { statusMessage = 'Browser reminders remain off until permission is allowed.'; render(); return; }
  state.reminders = { ...state.reminders, enabled: true }; statusMessage = 'Browser reminders are on while the app is open.'; await persist(); render();
}

async function stopRemindersToday(): Promise<void> { state.reminders = { ...state.reminders, stoppedDate: today() }; statusMessage = 'Reminders are stopped for today.'; await persist(); await closeReminderNotifications(); render(); }
async function disableReminders(): Promise<void> { state.reminders = { ...state.reminders, enabled: false }; statusMessage = 'Reminders are off.'; await persist(); await closeReminderNotifications(); render(); }
async function snoozeReminder(): Promise<void> { state.reminders = { ...state.reminders, snoozeUntil: Date.now() + state.reminders.snoozeMinutes * 60_000 }; statusMessage = 'Your reminder is snoozed.'; await persist(); render(); }

async function closeReminderNotifications(): Promise<void> {
  if (!webRegistration) return;
  const notices = await webRegistration.getNotifications(); notices.filter((notice) => notice.tag.startsWith('steady-')).forEach((notice) => notice.close());
}

function scheduleReminder(): void {
  if (timer) clearTimeout(timer); timer = null;
  if (!state.reminders.enabled || !webRegistration || !('Notification' in window) || Notification.permission !== 'granted') return;
  const next = nextReminder(new Date(), state.reminders, pendingToday()); if (!next) return;
  timer = setTimeout(() => { void deliverReminder(next.slotKey); }, Math.min(Math.max(next.at.getTime() - Date.now(), 250), 2_147_000_000));
}

async function deliverReminder(slotKey: string): Promise<void> {
  const now = new Date();
  if (!mayDeliver(now, state.reminders, slotKey, pendingToday())) { scheduleReminder(); return; }
  state.reminders = recordDelivery(state.reminders, now, slotKey);
  await repo.save(state); // Save the dedupe key before showing, so a reload cannot resend this slot.
  try { await webRegistration?.showNotification('A gentle check-in', { body: 'Take a look at your plan. One small step is enough.', tag: `steady-${slotKey}`, data: { slotKey }, actions: [{ action: 'snooze', title: 'Snooze' }, { action: 'stop', title: 'Stop today' }] } as NotificationOptions); }
  catch { statusMessage = 'This browser could not show the reminder.'; }
  scheduleReminder();
}

async function handleReminderAction(action: string): Promise<void> {
  if (action === 'snooze') await snoozeReminder();
  else if (action === 'stop') await stopRemindersToday();
  else { page = 'Today'; render(); }
}

async function start(): Promise<void> {
  state = await repo.load();
  if (supabase) {
    const { data: { session } } = await supabase.auth.getSession();
    accountEmail = session?.user.email ?? '';
    syncStatus = navigator.onLine ? (accountEmail ? 'syncing' : 'offline') : 'offline';
    if (accountEmail) void runSync();
    supabase.auth.onAuthStateChange((_event, current) => { accountEmail = current?.user.email ?? ''; if (accountEmail) void runSync(); else render(); });
  }
  window.addEventListener('online', () => { syncStatus = 'syncing'; void runSync(); });
  window.addEventListener('offline', () => { syncStatus = 'offline'; render(); });
  render();
  if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) {
    try { webRegistration = await navigator.serviceWorker.register('/sw.js'); await navigator.serviceWorker.ready; scheduleReminder(); } catch { /* Web reminders stay unavailable if service workers are blocked. */ }
    navigator.serviceWorker.addEventListener('message', (event: MessageEvent) => { if (event.data?.type === 'reminder-action') void handleReminderAction(event.data.action); });
  }
  const url = new URL(location.href); const action = url.searchParams.get('reminder-action');
  if (action) { window.history.replaceState({}, '', location.pathname); await handleReminderAction(action); }
  window.addEventListener('focus', scheduleReminder);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) scheduleReminder(); });
  window.setInterval(scheduleReminder, 60_000);
}

void start();
