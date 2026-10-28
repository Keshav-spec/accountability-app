import './style.css';
import './stage2.css';
import './stage3.css';
import './stage4.css';
import { today, setTaskStatus, validateTaskTitle, type Task, type DailyCheckIn } from './models.js';
import { mayDeliver, nextReminder, recordDelivery } from './reminders.js';
import { defaultState, LocalRepository, type AppState } from './localRepository';
import { getLastSyncAt, loadProfileName, requestGoogleSignIn, saveProfileName, supabase, syncState, type SyncStatus } from './supabaseSync.js';

const repo = new LocalRepository();
let state: AppState = defaultState();
let activeScope = 'guest';
let loadingScope: string | null = null;
let scopeLoadPromise: Promise<void> | null = null;
let page: 'Today' | 'Tasks' | 'Check-in' | 'Reminders' | 'Usage' | 'Account' = 'Today';
let editingId: string | null = null;
let editorOpen = false;
let webRegistration: ServiceWorkerRegistration | null = null;
let timer: ReturnType<typeof setTimeout> | null = null;
let statusMessage = '';
let syncStatus: SyncStatus = navigator.onLine ? 'needs attention' : 'offline';
let accountEmail = '';
let displayName = '';
let creatingAccount = false;
let syncActive = false;
let syncQueued = false;
let recoveryMode = false;
let recentlyDeleted: Task | null = null;
let recentlyChanged: Task | null = null;

const escapeHtml = (value: string) => value.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
const checkInForToday = (): DailyCheckIn => state.checkIns[today()] ?? { date: today(), morningPlan: null, priorities: [], progressNote: null, eveningReflection: null, dayOutcome: null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
const pendingToday = () => state.tasks.some((task) => !task.deletedAt && task.scheduledDate === today() && task.status === 'planned');

async function persist(): Promise<void> { await repo.save(state, activeScope); scheduleReminder(); if (supabase && accountEmail) void runSync(); }

async function switchLocalScope(userId: string | null): Promise<void> {
  const nextScope = userId || 'guest';
  if (nextScope === activeScope) { if (loadingScope === nextScope) await scopeLoadPromise; return; }
  activeScope = nextScope;
  state = defaultState(nextScope === 'guest');
  render();
  loadingScope = nextScope;
  const requestedScope = nextScope;
  scopeLoadPromise = repo.load(requestedScope).then((loaded) => {
    if (activeScope === requestedScope) { state = loaded; render(); }
  });
  try { await scopeLoadPromise; }
  finally { if (loadingScope === requestedScope) { loadingScope = null; scopeLoadPromise = null; } }
}

async function runSync(): Promise<void> {
  if (!supabase || !accountEmail) return;
  if (syncActive) { syncQueued = true; return; }
  syncActive = true;
  do {
    const syncScope = activeScope;
    const syncSnapshot = state;
    syncQueued = false; syncStatus = 'syncing'; render();
    try { syncStatus = await syncState(syncSnapshot, (next) => repo.save(next, syncScope)); }
    catch { syncStatus = navigator.onLine ? 'needs attention' : 'offline'; }
  } while (syncQueued && navigator.onLine);
  syncActive = false; render();
}

async function accountAction(action: 'signup' | 'signin'): Promise<void> {
  if (!supabase) return;
  const form = document.querySelector<HTMLFormElement>('#account-form'); if (!form) return;
  const data = new FormData(form); const email = String(data.get('email') ?? '').trim(); const password = String(data.get('password') ?? '');
  const name = String(data.get('displayName') ?? '').trim();
  if (action === 'signup' && (!name || name.length > 80)) { statusMessage = 'Enter a name between 1 and 80 characters.'; render(); return; }
  const result = action === 'signup'
    ? await supabase.auth.signUp({ email, password, options: { data: { display_name: name } } })
    : await supabase.auth.signInWithPassword({ email, password });
  if (result.error) { statusMessage = result.error.message; render(); return; }
  if (!result.data.session) {
    displayName = name;
    statusMessage = 'Check your email to confirm your account, then sign in. Your name is saved with your account.';
    render();
    return;
  }
  accountEmail = result.data.user?.email ?? email;
  await switchLocalScope(result.data.user?.id ?? null);
  {
    try {
      displayName = action === 'signup' ? name : (await loadProfileName()) ?? accountEmail.split('@')[0].replace(/[._-]/g, ' ');
      if (action === 'signup') await saveProfileName(name);
      statusMessage = action === 'signup' ? 'Account created. Your profile name is saved.' : 'Signed in. Syncing your local plan.';
      localStorage.setItem(`steady-profile-name:${accountEmail.toLowerCase()}`, displayName);
    } catch {
      statusMessage = 'Signed in. Your local plan can sync, but the profile name could not be loaded.';
    }
  }
  render(); if (result.data.session) await runSync();
}

async function requestPasswordReset(): Promise<void> {
  if (!supabase) return;
  const email = String(new FormData(document.querySelector<HTMLFormElement>('#account-form')!).get('email') ?? '').trim();
  if (!email) { statusMessage = 'Enter your email address first.'; render(); return; }
  const { error } = await supabase.auth.resetPasswordForEmail(email, { redirectTo: `${location.origin}/?recovery=1` });
  statusMessage = error ? 'Password reset could not be requested. Check your connection and try again.' : 'If an account uses that email, a password reset link is on its way.';
  render();
}

async function saveRecoveredPassword(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const form = event.currentTarget as HTMLFormElement; const data = new FormData(form);
  const password = String(data.get('password') ?? '');
  if (password.length < 8 || password !== String(data.get('confirm') ?? '')) { statusMessage = 'Use at least 8 characters and make both passwords match.'; render(); return; }
  const { error } = await supabase!.auth.updateUser({ password });
  if (error) { statusMessage = 'Password could not be updated. Reopen the latest reset link and try again.'; render(); return; }
  recoveryMode = false; history.replaceState({}, '', location.pathname);
  statusMessage = 'Your password was updated. You can sign in with it now.'; await signOut();
}

async function signOut(): Promise<void> { await supabase?.auth.signOut(); accountEmail = ''; displayName = ''; await switchLocalScope(null); syncStatus = 'offline'; render(); }
const profileNameKey = (email: string) => `steady-profile-name:${email.toLowerCase()}`;
const pendingProfileNameKey = (email: string) => `steady-profile-name-pending:${email.toLowerCase()}`;

async function syncPendingProfileName(): Promise<void> {
  if (!accountEmail || !navigator.onLine) return;
  const key = pendingProfileNameKey(accountEmail);
  const pending = localStorage.getItem(key);
  if (!pending) return;
  try { await saveProfileName(pending); localStorage.removeItem(key); }
  catch { /* Keep the local edit queued for a later connection. */ }
}

function render(): void {
  const app = document.querySelector<HTMLDivElement>('#app')!;
  const tabs = ['Today', 'Tasks', 'Check-in', 'Reminders', 'Usage'] as const;
  const icon: Record<(typeof tabs)[number], string> = { Today: '◷', Tasks: '✓', 'Check-in': '♡', Reminders: '◌', Usage: '◉' };
  const pageContent = page === 'Today' ? todayPage() : page === 'Tasks' ? tasksPage() : page === 'Check-in' ? checkInPage() : page === 'Reminders' ? remindersPage() : page === 'Usage' ? usagePage() : accountPanel();
  const dateLabel = new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
  app.innerHTML = `<div class="layout"><main><header class="app-header"><a class="brand" href="#" aria-label="Steady home"><img class="brand-mark" src="/steady-icon.png" alt=""><span class="brand-copy"><strong>STEADY</strong><small>${page === 'Account' ? 'Account & sync' : page}</small></span></a><div class="header-context"><span class="eyebrow">YOUR SPACE · ${escapeHtml(dateLabel.toUpperCase())}</span></div><div class="account-strip">${accountEmail ? `<span class="sync-state">${escapeHtml(syncStatus)}</span><button class="small-button" id="sync-now">Sync now</button>` : `<span class="sync-state">${navigator.onLine ? 'Local first' : 'Offline'}</span>`}<button class="profile-button" id="account-toggle" aria-label="Open account">${accountEmail ? escapeHtml((displayName || accountEmail).trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase() ?? '').join('') || 'A') : '♙'}</button>${accountEmail ? '<button class="small-button subtle-button" id="signout">Sign out</button>' : ''}</div></header><section class="content">${pageContent}${statusMessage ? `<p class="status-message" role="status">${escapeHtml(statusMessage)}${recentlyDeleted || recentlyChanged ? ' <button class="small-button" id="undo-task-change">Undo</button>' : ''}</p>` : ''}<footer>Your plan can change as the day unfolds.</footer></section><nav class="bottom-nav" aria-label="Main navigation">${tabs.map((tab) => `<button class="nav-link ${page === tab ? 'active' : ''}" data-page="${tab}" ${page === tab ? 'aria-current="page"' : ''}><span class="nav-dot" aria-hidden="true">${icon[tab]}</span><span>${tab}</span></button>`).join('')}</nav></main></div>`;
  app.querySelectorAll<HTMLButtonElement>('[data-page]').forEach((button) => button.addEventListener('click', () => { page = button.dataset.page as typeof page; editingId = null; editorOpen = false; render(); }));
  app.querySelectorAll<HTMLButtonElement>('[data-status]').forEach((button) => button.addEventListener('click', () => changeStatus(button.dataset.id!, button.dataset.status as Task['status'])));
  app.querySelectorAll<HTMLButtonElement>('[data-delete]').forEach((button) => button.addEventListener('click', () => void deleteTask(button.dataset.delete!)));
  app.querySelectorAll<HTMLButtonElement>('[data-edit]').forEach((button) => button.addEventListener('click', () => { editingId = button.dataset.edit === 'new' ? null : button.dataset.edit!; editorOpen = true; render(); }));
  app.querySelector<HTMLFormElement>('#task-form')?.addEventListener('submit', saveTask);
  app.querySelector<HTMLButtonElement>('#cancel-edit')?.addEventListener('click', () => { editingId = null; editorOpen = false; render(); });
  app.querySelector<HTMLFormElement>('#checkin-form')?.addEventListener('submit', saveCheckIn);
  app.querySelector<HTMLFormElement>('#reminder-form')?.addEventListener('submit', saveReminderSettings);
  app.querySelector<HTMLButtonElement>('#account-toggle')?.addEventListener('click', () => { page = page === 'Account' ? 'Today' : 'Account'; render(); });
  app.querySelector<HTMLButtonElement>('#signout')?.addEventListener('click', () => void signOut());
  app.querySelector<HTMLButtonElement>('#signout-account')?.addEventListener('click', () => void signOut());
  app.querySelector<HTMLButtonElement>('#sync-now')?.addEventListener('click', () => void runSync());
  app.querySelector<HTMLButtonElement>('#undo-task-change')?.addEventListener('click', () => void undoTaskChange());
  app.querySelector<HTMLButtonElement>('#account-sync-now')?.addEventListener('click', () => void runSync());
  app.querySelector<HTMLButtonElement>('#signin')?.addEventListener('click', () => void accountAction('signin'));
  app.querySelector<HTMLButtonElement>('#signup')?.addEventListener('click', () => void accountAction('signup'));
  app.querySelector<HTMLButtonElement>('#google-signin')?.addEventListener('click', () => void requestGoogleSignIn().catch(() => { statusMessage = 'Google sign-in could not start. Check that Google is enabled in Supabase Auth.'; render(); }));
  app.querySelector<HTMLButtonElement>('#link-google')?.addEventListener('click', () => void supabase?.auth.linkIdentity({ provider: 'google', options: { redirectTo: location.origin } }).then(({ error }) => { statusMessage = error ? 'Google could not be linked. Check Supabase Auth identity-linking settings.' : 'Continue with Google to link it to this account.'; render(); }));
  app.querySelector<HTMLButtonElement>('#forgot-password')?.addEventListener('click', () => void requestPasswordReset());
  app.querySelector<HTMLFormElement>('#recovery-form')?.addEventListener('submit', saveRecoveredPassword);
  app.querySelector<HTMLButtonElement>('#toggle-account-mode')?.addEventListener('click', () => { creatingAccount = !creatingAccount; statusMessage = ''; render(); });
  app.querySelector<HTMLButtonElement>('#enable-browser-reminders')?.addEventListener('click', enableBrowserReminders);
  app.querySelector<HTMLButtonElement>('#stop-today')?.addEventListener('click', stopRemindersToday);
  app.querySelector<HTMLButtonElement>('#disable-reminders')?.addEventListener('click', () => void disableReminders());
  app.querySelector<HTMLFormElement>('#profile-form')?.addEventListener('submit', saveName);
}

async function saveName(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const form = event.currentTarget as HTMLFormElement;
  const name = String(new FormData(form).get('displayName') ?? '').trim();
  if (!name || name.length > 80) { statusMessage = 'Enter a name between 1 and 80 characters.'; render(); return; }
  displayName = name;
  if (accountEmail) {
    localStorage.setItem(profileNameKey(accountEmail), name);
    localStorage.setItem(pendingProfileNameKey(accountEmail), name);
    await syncPendingProfileName();
  }
  statusMessage = accountEmail && localStorage.getItem(pendingProfileNameKey(accountEmail))
    ? 'Your name is saved on this device and will sync when you are online.'
    : 'Your profile name was updated.';
  render();
}

function accountPanel(): string {
  const intro = `<div class="page-intro"><p class="eyebrow">YOUR SPACE · PRIVATE SYNC</p><h1>Account & sync</h1><p class="subhead">Your plan stays on this device and syncs when you sign in online.</p></div>`;
  if (!supabase) return `${intro}<article class="check-card"><h2>Local-first planning</h2><p>Supabase is not configured. Your local planner remains available. Add the project URL and publishable key to <code>web/.env.local</code> to enable accounts and sync.</p></article>`;
  if (recoveryMode) return `${intro}<form id="recovery-form" class="check-card account-form"><h2>Choose a new password</h2><label>New password<input name="password" type="password" minlength="8" autocomplete="new-password" required></label><label>Confirm password<input name="confirm" type="password" minlength="8" autocomplete="new-password" required></label><button class="primary-button" type="submit">Save password</button></form>`;
  if (accountEmail) return `${intro}<article class="check-card profile-card"><div class="profile-summary"><span class="profile-avatar">${escapeHtml((displayName || accountEmail).trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase() ?? '').join('') || 'S')}</span><div><h2>${escapeHtml(displayName || 'Steady member')}</h2><p>${escapeHtml(accountEmail)}</p></div></div><p>Your tasks and check-ins are stored locally first and sync when a connection is available.</p><form id="profile-form"><label>Your name<input name="displayName" maxlength="80" required value="${escapeHtml(displayName)}" autocomplete="name"></label><div class="form-actions"><button class="primary-button" type="submit">Save name</button><button class="small-button" id="account-sync-now" type="button">Sync now</button><button class="small-button subtle-button" id="signout-account" type="button">Sign out</button></div></form><button class="small-button" id="link-google" type="button">Link Google sign-in</button><div class="account-sync-card"><span class="sync-state">${escapeHtml(syncStatus)}</span><p>${syncStatus === 'synced' ? 'Your local plan is up to date.' : 'Your local work remains available on this device.'}</p><p>Last successful sync: ${getLastSyncAt() ? escapeHtml(new Date(getLastSyncAt()!).toLocaleString()) : 'Not yet synced on this browser'}</p></div></article>`;
  return `${intro}${syncStatus === 'needs attention' ? '<article class="check-card">Sync needs attention. Check your connection, account sign-in, and Supabase setup. Your local edits remain saved here.</article>' : ''}<form id="account-form" class="check-card account-form"><h2>${creatingAccount ? 'Create your Steady account' : 'Welcome back'}</h2><p>${creatingAccount ? 'Choose the name you would like to see in Steady.' : 'Sign in to sync your tasks and check-ins across devices.'}</p>${creatingAccount ? '<label>Your name<input name="displayName" type="text" maxlength="80" autocomplete="name" required></label>' : ''}<label>Email<input name="email" type="email" autocomplete="email" required></label><label>Password<input name="password" type="password" autocomplete="${creatingAccount ? 'new-password' : 'current-password'}" minlength="8" required></label><div class="form-actions"><button class="primary-button" id="${creatingAccount ? 'signup' : 'signin'}" type="button">${creatingAccount ? 'Create account' : 'Sign in'}</button>${!creatingAccount ? '<button class="small-button" id="forgot-password" type="button">Forgot password?</button>' : ''}<button class="small-button" id="toggle-account-mode" type="button">${creatingAccount ? 'Already have an account? Sign in' : 'New to Steady? Create an account'}</button></div>${!creatingAccount ? '<button class="small-button" id="google-signin" type="button">Continue with Google</button>' : ''}</form>`;
}

function usagePage(): string {
  return `<div class="page-intro"><p class="eyebrow">YOUR SPACE · DIGITAL BOUNDARIES</p><h1>Mindful limits & pauses</h1><p class="subhead">A gentle pause for the apps you choose. Your local plan remains available wherever you use Steady.</p></div><article class="check-card platform-card"><div class="platform-heading"><span class="platform-icon">◉</span><div><h2>Device-first usage totals</h2><p>Available in Steady for Android</p></div><span class="platform-badge">ANDROID</span></div><p>Android can show daily totals for apps you select after you grant Usage Access in system Settings. Totals stay on that device and may be delayed or approximate.</p><p>This browser app cannot read which apps are running on your device. Usage totals and their thresholds are not collected or synced here.</p></article><article class="check-card platform-card"><div class="platform-heading"><span class="platform-icon">◌</span><div><h2>App blocking & gentle pauses</h2><p>Optional Android feature · off by default</p></div><span class="platform-badge">ANDROID</span></div><p>On Android, you can choose apps and a task or break-time rule, then grant Accessibility access after reading the disclosure. Steady checks only the foreground app name to show a bypassable pause screen.</p><p>Blocking is an accountability aid, not a security boundary. It can be paused, bypassed, disabled, or uninstalled.</p></article><article class="check-card local-boundary"><h2>Works across your devices</h2><p>Tasks, daily planning, check-ins, and reminders remain available in this web app. Usage totals and app pause settings are stored locally on Android and are not synced.</p><button class="primary-button" type="button" data-page="Today">Return to today</button></article>`;
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
  if (old) { recentlyChanged = old; recentlyDeleted = null; state.tasks = state.tasks.map((item) => item.id === old.id ? task : item); } else state.tasks.push(task);
  editingId = null; editorOpen = false; statusMessage = 'Task saved on this device.'; await persist(); render();
}

async function changeStatus(id: string, status: Task['status']): Promise<void> {
  recentlyChanged = state.tasks.find((task) => task.id === id) ?? null; recentlyDeleted = null;
  state.tasks = state.tasks.map((task) => task.id === id ? { ...setTaskStatus(task, status), syncPending: true } : task);
  statusMessage = status === 'completed' ? 'Marked complete. You can undo this any time.' : status === 'skipped' ? 'Skipped for now. That’s okay.' : 'Task moved back to your plan.';
  await persist(); render();
}

async function deleteTask(id: string): Promise<void> {
  const stamp = new Date().toISOString();
  recentlyDeleted = state.tasks.find((task) => task.id === id) ?? null;
  recentlyChanged = null;
  state.tasks = state.tasks.map((task) => task.id === id ? { ...task, deletedAt: stamp, updatedAt: stamp, syncPending: true } : task);
  statusMessage = 'Task deleted.'; await persist(); render();
}

async function undoTaskChange(): Promise<void> {
  const snapshot = recentlyDeleted ?? recentlyChanged;
  if (!snapshot) return;
  state.tasks = state.tasks.map((task) => task.id === snapshot.id ? { ...snapshot, updatedAt: new Date().toISOString(), syncPending: true } : task);
  recentlyDeleted = null; recentlyChanged = null; statusMessage = 'Your task change was undone.'; await persist(); render();
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
  const initialSession = supabase ? (await supabase.auth.getSession()).data.session : null;
  let initialUserId: string | null = initialSession?.user.id ?? null;
  activeScope = initialUserId ?? 'guest';
  state = await repo.load(activeScope);
  if (supabase) {
    const { data: { session } } = await supabase.auth.getSession();
    initialUserId = session?.user.id ?? null;
    accountEmail = session?.user.email ?? '';
    syncStatus = navigator.onLine ? (accountEmail ? 'syncing' : 'offline') : 'offline';
    if (accountEmail) {
      const emailForProfile = accountEmail;
      const key = profileNameKey(accountEmail);
      displayName = localStorage.getItem(key) ?? '';
      void loadProfileName().then((name) => {
        if (name && accountEmail.toLowerCase() === emailForProfile.toLowerCase()) {
          displayName = name;
          localStorage.setItem(key, name);
          render();
        }
      }).catch(() => undefined);
      void syncPendingProfileName();
    }
    supabase.auth.onAuthStateChange((event, current) => {
      if (event === 'PASSWORD_RECOVERY') { recoveryMode = true; page = 'Account'; render(); return; }
      accountEmail = current?.user.email ?? '';
      void switchLocalScope(current?.user.id ?? null).then(() => {
      if (accountEmail) {
        displayName = localStorage.getItem(profileNameKey(accountEmail)) ?? '';
        void loadProfileName().then((name) => { if (name) { displayName = name; localStorage.setItem(profileNameKey(accountEmail), name); render(); } }).catch(() => undefined);
        void syncPendingProfileName();
        void runSync();
      } else { displayName = ''; render(); }
      });
    });
  }
  if (accountEmail) void runSync();
  window.addEventListener('online', () => { syncStatus = 'syncing'; void syncPendingProfileName(); void runSync(); });
  window.addEventListener('offline', () => { syncStatus = 'offline'; render(); });
  render();
  if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) {
    try { webRegistration = await navigator.serviceWorker.register('/sw.js'); await navigator.serviceWorker.ready; scheduleReminder(); } catch { /* Web reminders stay unavailable if service workers are blocked. */ }
    navigator.serviceWorker.addEventListener('message', (event: MessageEvent) => { if (event.data?.type === 'reminder-action') void handleReminderAction(event.data.action); });
  }
  const url = new URL(location.href); const action = url.searchParams.get('reminder-action');
  if (url.searchParams.get('recovery') === '1') { recoveryMode = true; page = 'Account'; history.replaceState({}, '', location.pathname); }
  if (action) { window.history.replaceState({}, '', location.pathname); await handleReminderAction(action); }
  window.addEventListener('focus', scheduleReminder);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) scheduleReminder(); });
  window.setInterval(scheduleReminder, 60_000);
}

void start();

