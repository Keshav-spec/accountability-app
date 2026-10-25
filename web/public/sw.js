const CACHE = 'steady-shell-v2';
self.addEventListener('install', (event) => event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(['/', '/manifest.webmanifest', '/icon.svg']))));
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));
self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET' || new URL(event.request.url).origin !== self.location.origin) return;
  event.respondWith(caches.match(event.request).then((cached) => cached ?? fetch(event.request).then((response) => {
    if (response.ok) { const copy = response.clone(); caches.open(CACHE).then((cache) => cache.put(event.request, copy)); }
    return response;
  }).catch(() => caches.match('/'))));
});
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const action = event.action === 'snooze' ? 'snooze' : event.action === 'stop' ? 'stop' : 'open';
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const client = windows[0];
    if (client) { await client.focus(); client.postMessage({ type: 'reminder-action', action }); }
    else await self.clients.openWindow(`/?reminder-action=${action}`);
  })());
});
