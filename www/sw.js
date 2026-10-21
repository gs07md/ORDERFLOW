/* To Do List — service worker: offline shell + push reminders */
const VER = 'v13';
const SHELL = ['/', '/index.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png', '/icons/badge-96.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VER).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== VER && k !== 'fonts').map(k => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  if (url.pathname.startsWith('/api/')) return;
  if (url.hostname.includes('fonts.g')) {
    e.respondWith(caches.open('fonts').then(async c => {
      const hit = await c.match(e.request);
      if (hit) return hit;
      const r = await fetch(e.request);
      if (r.ok || r.type === 'opaque') c.put(e.request, r.clone());
      return r;
    }));
    return;
  }
  if (url.origin !== location.origin) return;
  // network first, fall back to cache (so updates arrive, app works offline)
  e.respondWith(fetch(e.request).then(r => {
    if (r.ok) { const cp = r.clone(); caches.open(VER).then(c => c.put(e.request, cp)); }
    return r;
  }).catch(() => caches.match(e.request).then(r => r || caches.match('/index.html'))));
});

/* ---- tiny idb ---- */
function idb() { return new Promise((res, rej) => { const r = indexedDB.open('aajnukaam', 1); r.onupgradeneeded = () => r.result.createObjectStore('kv'); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); }); }
async function idbGet(k) { const db = await idb(); return new Promise(r => { const q = db.transaction('kv').objectStore('kv').get(k); q.onsuccess = () => r(q.result); q.onerror = () => r(undefined); }); }
async function idbSet(k, v) { const db = await idb(); return new Promise(r => { const t = db.transaction('kv', 'readwrite'); t.objectStore('kv').put(v, k); t.oncomplete = r; t.onerror = r; }); }

self.addEventListener('push', e => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch (err) { d = { title: e.data && e.data.text() }; }
  e.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const focused = wins.find(w => w.focused && w.visibilityState === 'visible');
    if (focused && d.kind === 'due') {
      // app is open on screen: let the in-app ring handle it
      focused.postMessage({ type: 'ring', id: d.id });
      return;
    }
    const opts = {
      body: d.body || '',
      tag: d.tag || ('t-' + (d.id || Date.now())),
      renotify: true,
      requireInteraction: d.kind === 'due',
      vibrate: d.kind === 'due' ? [500, 200, 500, 200, 800] : [200],
      icon: '/icons/icon-192.png',
      badge: '/icons/badge-96.png',
      timestamp: d.due || Date.now(),
      data: d,
    };
    if (d.kind === 'due') opts.actions = [{ action: 'done', title: '✓ Done' }, { action: 'snooze', title: '⏰ In 10 min' }];
    await self.registration.showNotification(d.title || 'To Do List', opts);
  })());
});

self.addEventListener('notificationclick', e => {
  const n = e.notification, d = n.data || {};
  n.close();
  e.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    if ((e.action === 'done' || e.action === 'snooze') && d.id) {
      const acts = (await idbGet('actions')) || [];
      acts.push({ id: d.id, action: e.action, due: d.due, at: Date.now() });
      await idbSet('actions', acts);
      try {
        await fetch('/api/action', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ deviceId: d.deviceId, id: d.id, due: d.due, action: e.action }) });
      } catch (err) {}
      wins.forEach(w => w.postMessage({ type: 'actions' }));
      return;
    }
    const url = d.kind === 'due' && d.id ? '/?ring=' + encodeURIComponent(d.id) : '/';
    const w = wins[0];
    if (w) { await w.focus(); if (d.kind === 'due') w.postMessage({ type: 'ring', id: d.id }); return; }
    await self.clients.openWindow(url);
  })());
});
