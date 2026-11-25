// Bump VERSION whenever you publish a new version of the site.
var VERSION = 'gradetrack-v5';
var CORE = ['./', 'index.html', 'manifest.webmanifest', 'icons/icon.svg'];

self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(VERSION).then(function (c) { return c.addAll(CORE); }).then(function () { return self.skipWaiting(); }));
});
self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (k) { return k !== VERSION; }).map(function (k) { return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});
self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET') return;
  var url = new URL(req.url);
  if (url.pathname.indexOf('/api/') >= 0) return;   // calls to the Java server are never cached
  var sameOrigin = url.origin === location.origin;
  var font = /fonts\.(googleapis|gstatic)\.com$/.test(url.hostname);
  if (!sameOrigin && !font) return;
  // Pages: network first, so an update shows up as soon as you are online. Saved copy only when offline.
  if (sameOrigin && (req.mode === 'navigate' || /\/(index\.html)?$/.test(url.pathname) || /sw\.js$/.test(url.pathname))) {
    e.respondWith(caches.open(VERSION).then(function (cache) {
      return fetch(req, { cache: 'no-cache' }).then(function (res) {
        if (res && res.ok) cache.put(req, res.clone());
        return res;
      }).catch(function () {
        return cache.match(req, { ignoreSearch: true }).then(function (hit) { return hit || cache.match('index.html'); });
      });
    }));
    return;
  }
  e.respondWith(caches.open(VERSION).then(function (cache) {
    return cache.match(req, { ignoreSearch: true }).then(function (hit) {
      var net = fetch(req).then(function (res) {
        if (res && (res.ok || res.type === 'opaque')) cache.put(req, res.clone());
        return res;
      }).catch(function () { return hit || (req.mode === 'navigate' ? cache.match('index.html') : undefined); });
      return hit || net;
    });
  }));
});
