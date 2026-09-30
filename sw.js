/* Service worker — permet l'installation et un fonctionnement hors-ligne basique.
   Dispositif de mise à jour : le document principal (index.html / la racine) est servi en mode
   "réseau d'abord, cache en secours" -- toute mise à jour publiée (modification, ajout ou suppression)
   est donc prise en compte dès la prochaine ouverture normale de l'application tant que l'appareil est
   en ligne ; hors-ligne, la dernière version mise en cache reste servie. Les autres fichiers de l'app
   shell (icônes, manifest — qui changent rarement) restent en "cache d'abord, réseau en secours" pour
   un chargement instantané. Les appels réseau vers Google Drive/Sheets (googleapis.com,
   accounts.google.com) et vers les polices Google Fonts ne sont volontairement PAS interceptés : ils
   passent toujours par le réseau. */
var CACHE_NAME = 'carnet-inspection-v19';
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
  var req = event.request;
  var url = req.url;
  if(req.method !== 'GET') return;
  if(url.indexOf(self.location.origin) !== 0) return; // laisser passer tout appel externe (Google, polices…)

  var isAppDoc = req.mode === 'navigate' || url.endsWith('/index.html') || url.endsWith('/');
  if(isAppDoc){
    event.respondWith(
      fetch(req).then(function(res){
        if(res && res.status===200){
          var copy = res.clone();
          caches.open(CACHE_NAME).then(function(cache){ cache.put(req, copy); });
        }
        return res;
      }).catch(function(){
        return caches.match(req).then(function(cached){ return cached || caches.match('./index.html'); });
      })
    );
    return;
  }

  event.respondWith(
    caches.match(req).then(function(cached){
      var network = fetch(req).then(function(res){
        if(res && res.status===200){
          var copy = res.clone();
          caches.open(CACHE_NAME).then(function(cache){ cache.put(req, copy); });
        }
        return res;
      }).catch(function(){ return cached; });
      return cached || network;
    })
  );
});
