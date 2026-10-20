/* Service worker — permet l'installation et un fonctionnement hors-ligne basique.
   Dispositif de mise à jour : le document principal (index.html / la racine) est servi en mode
   "réseau d'abord, cache en secours" -- toute mise à jour publiée (modification, ajout ou suppression)
   est donc prise en compte dès la prochaine ouverture normale de l'application tant que l'appareil est
   en ligne ; hors-ligne, la dernière version mise en cache reste servie. Les autres fichiers de l'app
   shell (icônes, manifest — qui changent rarement) restent en "cache d'abord, réseau en secours" pour
   un chargement instantané. Les appels réseau vers Google Drive/Sheets (googleapis.com,
   accounts.google.com) et vers les polices Google Fonts ne sont volontairement PAS interceptés : ils
   passent toujours par le réseau. */
var CACHE_NAME = 'carnet-inspection-v23';
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

/* Accélération multicœur de l'IA locale (voir localAiIsolationSync dans index.html) : sur les appareils
   où l'IA locale est choisie, l'application range un drapeau dans Cache Storage ; la page est alors
   servie avec l'en-tête Document-Isolation-Policy, qui rend la page « isolée » (crossOriginIsolated)
   et permet au moteur WebAssembly d'utiliser tous les cœurs du processeur. Sans drapeau, ou si le
   navigateur ne connaît pas cet en-tête, rien ne change. */
function withIaIsolation(res){
  if(!res || res.type === 'opaque' || res.type === 'opaqueredirect' || res.status !== 200) return res;
  return caches.open('ci-flags').then(function(c){ return c.match('./__ia-isolation'); }).then(function(flag){
    if(!flag) return res;
    var h = new Headers(res.headers);
    h.set('Document-Isolation-Policy', 'isolate-and-credentialless');
    return new Response(res.body, { status: res.status, statusText: res.statusText, headers: h });
  }).catch(function(){ return res; });
}

self.addEventListener('install', function(event){
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(function(cache){ return cache.addAll(APP_SHELL); }).catch(function(){})
  );
});

self.addEventListener('activate', function(event){
  event.waitUntil(
    caches.keys().then(function(keys){
      return Promise.all(keys.filter(function(k){ return k!==CACHE_NAME && k.indexOf('-ia')<0 && k!=='ci-flags'; }).map(function(k){ return caches.delete(k); }));
    }).then(function(){ return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function(event){
  var req = event.request;
  var url = req.url;
  if(req.method !== 'GET') return;
  /* Moteur de l'IA locale (wllama, version figée sur jsdelivr) : mis en cache au premier usage pour
     que la rédaction fonctionne ensuite hors connexion. Le modèle lui-même n'est pas concerné : il est
     stocké par l'application dans l'espace privé du navigateur (OPFS). */
  if(url.indexOf('https://cdn.jsdelivr.net/npm/@wllama/') === 0){
    event.respondWith(
      caches.open(CACHE_NAME + '-ia').then(function(cache){
        return cache.match(req).then(function(hit){
          return hit || fetch(req).then(function(res){ if(res && res.status===200) cache.put(req, res.clone()); return res; });
        });
      })
    );
    return;
  }
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
      }).then(function(res){ return req.mode === 'navigate' ? withIaIsolation(res) : res; })
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

/* Notifications des évènements imminents (voir checkImminentTaskNotifications/showTaskNotification
   dans l'application) : un tap sur la notification ramène au premier plan un onglet déjà ouvert de
   l'application plutôt que d'en ouvrir un nouveau, ou en ouvre un nouveau s'il n'y en a aucun. */
self.addEventListener('notificationclick', function(event){
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({type:'window', includeUncontrolled:true}).then(function(list){
      for(var i=0;i<list.length;i++){ if('focus' in list[i]) return list[i].focus(); }
      if(self.clients.openWindow) return self.clients.openWindow('./');
    })
  );
});
