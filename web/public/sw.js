const CACHE = 'steady-shell-v3';
self.addEventListener('install', (event) => event.waitUntil((async () => {
  const cache = await caches.open(CACHE);
  await cache.addAll(['/', '/manifest.webmanifest', '/icon.svg']);
  await self.skipWaiting();
})()));
self.addEventListener('activate', (event) => event.waitUntil((async () => {
  const names = await caches.keys();
  await Promise.all(names.filter((name) => name.startsWith('steady-shell-') && name !== CACHE).map((name) => caches.delete(name)));
  await self.clients.claim();
})()));
self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET' || new URL(event.request.url).origin !== self.location.origin) return;
  const url = new URL(event.request.url);
  const updateableAsset = event.request.mode === 'navigate' || /\.(?:js|css)$/.test(url.pathname) || url.pathname.startsWith('/src/');
  event.respondWith((async () => {
    if (updateableAsset) {
      try {
        const response = await fetch(event.request);
        if (response.ok) await (await caches.open(CACHE)).put(event.request, response.clone());
        return response;
      } catch {
        return (await caches.match(event.request)) ?? (event.request.mode === 'navigate' ? await caches.match('/') : Response.error());
      }
    }
    const cached = await caches.match(event.request);
    if (cached) return cached;
    const response = await fetch(event.request);
    if (response.ok) await (await caches.open(CACHE)).put(event.request, response.clone());
    return response;
  })());
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
