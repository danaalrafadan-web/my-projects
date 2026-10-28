// Service Worker: يخلي الموقع قابل للتثبيت على الجوال (Android / iOS / Windows)
// يخزّن واجهة الموقع فقط، أما البصمة فتحتاج اتصال بالخادم دائماً.
const CACHE = 'attendance-v1';
const SHELL = ['index.html', 'admin.html', 'style.css', 'common.js', 'icon.svg', 'manifest.webmanifest'];

self.addEventListener('install', e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL))));
self.addEventListener('activate', e => e.waitUntil(
  caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))));

self.addEventListener('fetch', e => {
  if (new URL(e.request.url).pathname.startsWith('/api/')) return; // لا نخزن بيانات الحضور
  e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
});
