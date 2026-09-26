/* Service worker minimal — permet l'installation et un fonctionnement hors-ligne basique.
   Stratégie "cache d'abord, réseau en secours" pour les fichiers de l'application (app shell).
   Les appels réseau vers Google Drive/Sheets (googleapis.com, accounts.google.com) et vers les
   polices Google Fonts ne sont volontairement PAS interceptés : ils passent toujours par le réseau. */
var CACHE_NAME = 'carnet-inspection-v3';
var APP_SHELL = [
  './',
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './icon-512-maskable.png',
  './icon-180.png',
  './favicon-32.png'
];

self.addEventListener('install', function(event){
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(function(cache){ return cache.addAll(APP_SHELL); }).catch(function(){})
  );
});

self.addEventListener('activate', function(event){
  event.waitUntil(
    caches.keys().then(function(keys){
      return Promise.all(keys.filter(function(k){ return k!==CACHE_NAME; }).map(function(k){ return caches.delete(k); }));
    }).then(function(){ return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function(event){
  var url = event.request.url;
  if(event.request.method !== 'GET') return;
  if(url.indexOf(self.location.origin) !== 0) return; // laisser passer tout appel externe (Google, polices…)
  event.respondWith(
    caches.match(event.request).then(function(cached){
      var network = fetch(event.request).then(function(res){
        if(res && res.status===200){
          var copy = res.clone();
          caches.open(CACHE_NAME).then(function(cache){ cache.put(event.request, copy); });
        }
        return res;
      }).catch(function(){ return cached; });
      return cached || network;
    })
  );
});
