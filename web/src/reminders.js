import { today } from './models.js';

const minutes = (time) => {
  const [hours, mins] = time.split(':').map(Number);
  return hours * 60 + mins;
};

export function isQuietAt(date, start, end) {
  const current = date.getHours() * 60 + date.getMinutes();
  const from = minutes(start); const to = minutes(end);
  if (from === to) return false;
  return from < to ? current >= from && current < to : current >= from || current < to;
}

export function moveOutOfQuietHours(date, start, end) {
  if (!isQuietAt(date, start, end)) return new Date(date);
  const [hours, mins] = end.split(':').map(Number);
  const next = new Date(date);
  const overnight = minutes(start) > minutes(end) && minutes(date.toTimeString().slice(0, 5)) >= minutes(start);
  if (overnight) next.setDate(next.getDate() + 1);
  next.setHours(hours, mins, 0, 0);
  return next;
}

export function nextReminder(now, settings, hasPlannedTasks) {
  if (!settings.enabled || !hasPlannedTasks || settings.dailyLimit < 1) return null;
  const deliveredToday = settings.deliveredDate === today(now);
  const count = deliveredToday ? settings.deliveredCount : 0;
  const sent = deliveredToday ? settings.deliveredSlots : [];
  const deferUntilTomorrow = settings.stoppedDate === today(now) || count >= settings.dailyLimit;
  const candidates = [];
  if (!deferUntilTomorrow && settings.snoozeUntil && settings.snoozeUntil > now.getTime()) {
    const date = moveOutOfQuietHours(new Date(settings.snoozeUntil), settings.quietStart, settings.quietEnd);
    candidates.push({ at: date, slotKey: `snooze:${settings.snoozeUntil}` });
  }
  let quietDeferralAdded = false;
  for (let offset = -1; offset <= 2; offset += 1) {
    const day = new Date(now); day.setHours(0, 0, 0, 0); day.setDate(day.getDate() + offset);
    if (deferUntilTomorrow && today(day) <= today(now)) continue;
    for (const time of [...settings.times].sort()) {
      const [hours, mins] = time.split(':').map(Number);
      const at = new Date(day); at.setHours(hours, mins, 0, 0);
      const slotKey = `${today(at)}_${time}`;
      if (sent.includes(slotKey)) continue;
      const moved = moveOutOfQuietHours(at, settings.quietStart, settings.quietEnd);
      if (at <= now && moved > now) {
        if (!quietDeferralAdded) candidates.push({ at: moved, slotKey });
        quietDeferralAdded = true;
      } else if (at > now) candidates.push({ at: moved, slotKey });
    }
  }
  return candidates.filter((candidate) => candidate.at > now).sort((a, b) => a.at - b.at)[0] ?? null;
}

export function mayDeliver(now, settings, slotKey, hasPlannedTasks) {
  if (!settings.enabled || !hasPlannedTasks || settings.stoppedDate === today(now) || isQuietAt(now, settings.quietStart, settings.quietEnd)) return false;
  if (settings.deliveredDate === today(now) && settings.deliveredCount >= settings.dailyLimit) return false;
  if (settings.deliveredDate === today(now) && settings.deliveredSlots.includes(slotKey)) return false;
  return true;
}

export function recordDelivery(settings, now, slotKey) {
  const sameDay = settings.deliveredDate === today(now);
  return {
    ...settings,
    deliveredDate: today(now),
    deliveredCount: (sameDay ? settings.deliveredCount : 0) + 1,
    deliveredSlots: [...(sameDay ? settings.deliveredSlots : []), slotKey],
    snoozeUntil: slotKey.startsWith('snooze:') ? null : settings.snoozeUntil,
  };
}
