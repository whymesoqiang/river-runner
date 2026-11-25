// Versioned application assets only. User records/favorites/settings live in
// localStorage and are never touched by installation, activation or cache cleanup.
const BUILD = '__RR_BUILD__';
const requestedRuntime = new URL(self.location.href).searchParams.get('runtime');
// Legacy pages register sw.js without a runtime. Cache both once for that migration.
const RUNTIME = ['js', 'wasm'].includes(requestedRuntime) ? requestedRuntime : 'both';
const CACHE = 'rr-' + BUILD + '-' + RUNTIME;
const VERSION = '__RR_VERSION__';
const RELEASE = new URL('release.json', self.registration.scope).href;
function pageKey(url) { return url.origin + url.pathname.replace(/index\.html$/, ''); }
self.addEventListener('install', event => event.waitUntil((async () => {
    const response = await fetch(RELEASE, { cache: 'no-store' });
    if (!response.ok) throw new Error('Release unavailable');
    const release = await response.json();
    if (release.build !== BUILD) throw new Error('Deployment not ready');
    const names = (await caches.keys()).filter(n => n.startsWith('rr-') && n !== CACHE);
    const old = await Promise.all(names.map(n => caches.open(n)));
    const modern = (await Promise.all(old.map(c => c.match(RELEASE)))).some(Boolean);
    const cache = await caches.open(CACHE);
    try {
        const assets = RUNTIME === 'both' ? [...new Set([...release.assets.js, ...release.assets.wasm])] : release.assets[RUNTIME];
        for (const path of assets) {
            const url = new URL(path, self.registration.scope);
            // Reuse only content-addressed binaries; named scripts/resources must be fresh.
            let result;
            if (/\/[0-9a-f]{16,}\.wasm$/.test(url.pathname)) {
                for (const previous of old) { result = await previous.match(url.href); if (result) break; }
            }
            if (!result) result = await fetch(url.href, { cache: 'reload' });
            if (!result.ok) throw new Error('Asset unavailable: ' + path);
            await cache.put(path === 'index.html' ? pageKey(url) : url.href, result);
        }
        await cache.put(RELEASE, new Response(JSON.stringify(release), { headers: { 'Content-Type': 'application/json' } }));
    } catch (error) { await caches.delete(CACHE); throw error; }
    // One-time migration: old versions have no update button. Later versions wait
    // for the user's explicit click, so an update never interrupts a training hand.
    if (!modern) await self.skipWaiting();
})()));
self.addEventListener('activate', event => event.waitUntil((async () => {
    // Retain one previous application cache; saves are never in these caches.
    const previous = (await caches.keys()).filter(n => n.startsWith('rr-') && n !== CACHE);
    for (const name of previous.slice(0, -1)) await caches.delete(name);
    await self.clients.claim();
})()));
self.addEventListener('fetch', event => {
    const req = event.request, url = new URL(req.url);
    if (req.method !== 'GET' || url.origin !== self.location.origin || /\/(sw\.js|release\.json)$/.test(url.pathname)) return;
    event.respondWith((async () => {
        const cache = await caches.open(CACHE);
        const key = req.mode === 'navigate' ? pageKey(url) : req;
        const hit = await cache.match(key, { ignoreSearch: req.mode === 'navigate' });
        if (hit) return hit;
        const response = await fetch(req);
        if (response.ok) { try { await cache.put(key, response.clone()); } catch (_) {} }
        return response;
    })());
});
self.addEventListener('message', event => {
    if (event.data?.type === 'version') { event.ports[0]?.postMessage({ build: BUILD, version: VERSION }); return; }
    if (event.data?.type !== 'activate') return;
    event.waitUntil((async () => {
        const windows = (await self.clients.matchAll({ type: 'window', includeUncontrolled: true })).filter(c => c.url.startsWith(self.registration.scope));
        if (windows.length > 1) { event.ports[0]?.postMessage('blocked'); return; }
        event.ports[0]?.postMessage('ok');
        await self.skipWaiting();
    })());
});
