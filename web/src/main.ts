import './style.css';
import { today, validateTaskTitle, type Task, type DailyCheckIn } from './models.js';

const storageKey = 'steady-stage1';
type State = { tasks: Task[]; checkIn: DailyCheckIn };
const now = new Date().toISOString();
const initial: State = {
  tasks: [
    { id: 'sample-water', title: 'Drink a glass of water', notes: null, scheduledDate: today(), targetTime: null, status: 'planned', createdAt: now, updatedAt: now },
    { id: 'sample-air', title: 'Step outside for a few minutes', notes: null, scheduledDate: today(), targetTime: null, status: 'planned', createdAt: now, updatedAt: now },
  ],
  checkIn: { date: today(), morningPlan: null, eveningReflection: null, createdAt: now, updatedAt: now },
};
let state = load();
let page: 'Today' | 'Tasks' | 'Check-in' = 'Today';

function load(): State {
  try {
    const stored = localStorage.getItem(storageKey);
    if (stored) return JSON.parse(stored) as State;
  } catch { /* A damaged local entry should not prevent a fresh start. */ }
  return structuredClone(initial);
}
function save(): void { localStorage.setItem(storageKey, JSON.stringify(state)); }
function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]!);
}
function render(): void {
  const app = document.querySelector<HTMLDivElement>('#app')!;
  const done = state.tasks.filter((task) => task.status === 'completed').length;
  app.innerHTML = `
    <div class="layout">
      <aside class="rail"><a class="brand" href="#" aria-label="Steady home"><span class="brand-mark">s</span><span>steady</span></a>
        <div class="rail-label">YOUR SPACE</div><nav>${(['Today', 'Tasks', 'Check-in'] as const).map((tab) => `<button class="nav-link ${page === tab ? 'active' : ''}" data-page="${tab}"><span class="nav-dot">${tab === 'Today' ? '◷' : tab === 'Tasks' ? '✓' : '♡'}</span>${tab}</button>`).join('')}</nav>
        <div class="rail-note"><span class="sun">☼</span><p>One step at a time is enough.</p></div>
      </aside>
      <main><header><div class="eyebrow">YOUR SPACE <span>·</span> ${new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).toUpperCase()}</div><div class="mobile-nav">${(['Today', 'Tasks', 'Check-in'] as const).map((tab) => `<button class="nav-link ${page === tab ? 'active' : ''}" data-page="${tab}">${tab}</button>`).join('')}</div></header>
      <section class="content">${page === 'Check-in' ? checkInPage() : homePage(done)}<footer>Your plan can change as the day unfolds.</footer></section></main>
    </div>`;
  app.querySelectorAll<HTMLButtonElement>('[data-page]').forEach((button) => button.addEventListener('click', () => { page = button.dataset.page as typeof page; render(); }));
  app.querySelector<HTMLButtonElement>('#add-task')?.addEventListener('click', addTask);
  app.querySelectorAll<HTMLInputElement>('[data-task]').forEach((input) => input.addEventListener('change', () => toggleTask(input.dataset.task!)));
  app.querySelector<HTMLTextAreaElement>('#morning')?.addEventListener('input', updateCheckIn);
  app.querySelector<HTMLTextAreaElement>('#evening')?.addEventListener('input', updateCheckIn);
}
function homePage(done: number): string {
  const list = state.tasks.length ? state.tasks.map((task) => `<label class="task-row"><input type="checkbox" data-task="${escapeHtml(task.id)}" ${task.status === 'completed' ? 'checked' : ''}><span class="task-copy ${task.status === 'completed' ? 'done' : ''}">${escapeHtml(task.title)}</span></label>`).join('') : '<p class="empty">Nothing on your list yet. Add one small thing when you’re ready.</p>';
  return `<div class="welcome"><div class="welcome-copy"><p class="eyebrow">A GENTLER WAY TO GET THINGS DONE</p><h1>A little structure,<br>with room to breathe.</h1><p class="subhead">Small steps count. So does taking a pause.</p></div><div class="orbit" aria-hidden="true"><span>✳</span></div></div>
    <article class="plan-card"><div class="card-heading"><div><p class="eyebrow">TODAY</p><h2>Your gentle plan</h2></div><div class="progress"><strong>${done}</strong> / ${state.tasks.length}</div></div><div class="task-list">${list}</div><button class="text-button" id="add-task">＋ &nbsp; Add a small task</button></article>
    <p class="soft-prompt"><span>✦</span> You don’t have to do it all. Just begin where you are.</p>`;
}
function checkInPage(): string {
  return `<div class="page-intro"><p class="eyebrow">A MOMENT FOR YOU</p><h1>Check in with yourself</h1><p class="subhead">No right answers. Just a little space to notice.</p></div><article class="check-card"><label for="morning">Morning plan</label><p>What would make today feel steady?</p><textarea id="morning" rows="3" placeholder="A small thing I’d like to make space for…">${escapeHtml(state.checkIn.morningPlan ?? '')}</textarea></article><article class="check-card"><label for="evening">Evening reflection</label><p>What went well, even a little?</p><textarea id="evening" rows="3" placeholder="Something I’m glad I did…">${escapeHtml(state.checkIn.eveningReflection ?? '')}</textarea></article><p class="saved-note" role="status">Saved on this device</p>`;
}
function addTask(): void {
  const title = window.prompt('What is one small thing you’d like to do?');
  if (title === null) return;
  const error = validateTaskTitle(title);
  if (error) { window.alert(error); return; }
  const stamp = new Date().toISOString();
  state.tasks.push({ id: crypto.randomUUID(), title: title.trim(), notes: null, scheduledDate: today(), targetTime: null, status: 'planned', createdAt: stamp, updatedAt: stamp });
  save(); render();
}
function toggleTask(id: string): void {
  const task = state.tasks.find((item) => item.id === id);
  if (!task) return;
  task.status = task.status === 'completed' ? 'planned' : 'completed'; task.updatedAt = new Date().toISOString(); save(); render();
}
function updateCheckIn(): void {
  const morningPlan = document.querySelector<HTMLTextAreaElement>('#morning')?.value ?? state.checkIn.morningPlan ?? '';
  const eveningReflection = document.querySelector<HTMLTextAreaElement>('#evening')?.value ?? state.checkIn.eveningReflection ?? '';
  state.checkIn = { ...state.checkIn, morningPlan: morningPlan || null, eveningReflection: eveningReflection || null, updatedAt: new Date().toISOString() };
  save();
}
render();
if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) navigator.serviceWorker.register('/sw.js').catch(() => {});
