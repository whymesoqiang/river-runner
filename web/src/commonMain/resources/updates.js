/* Only app assets are updated. Never remove localStorage, IndexedDB, or user saves. */
(function () {
    'use strict';
    var build = document.querySelector('meta[name="rr-build"]').content;
    var status = 'checking', version = '', registration = null, starting = null, checking = false, applying = false;
    var lastCheck = 0;
    function runtime() {
        return performance.getEntriesByType('resource').some(function (e) { return /originJsRiverRunner\.js/.test(e.name); }) ? 'js' : 'wasm';
    }
    function ready(worker) {
        if (!worker) return;
        var channel = new MessageChannel();
        channel.port1.onmessage = function (event) { version = event.data.version; status = event.data.build === build ? 'latest' : 'ready'; channel.port1.close(); };
        worker.postMessage({ type: 'version' }, [channel.port2]);
    }
    function watch(reg) {
        if (reg.waiting) ready(reg.waiting);
        function install(worker) {
            if (!worker) return;
            status = 'installing';
            worker.addEventListener('statechange', function () {
                if (worker.state === 'installed') { if (reg.waiting) ready(reg.waiting); else status = 'latest'; }
                if (worker.state === 'redundant') status = 'error';
            });
        }
        reg.addEventListener('updatefound', function () { install(reg.installing); });
        if (reg.installing) install(reg.installing);
    }
    async function start() {
        if (starting) return starting;
        starting = (async function () {
            if (!('serviceWorker' in navigator)) { status = 'error'; return; }
            registration = await navigator.serviceWorker.register('sw.js?runtime=' + runtime(), { updateViaCache: 'none' });
            watch(registration);
            await check();
        })().catch(function () { status = navigator.onLine ? 'error' : 'offline'; starting = null; });
        return starting;
    }
    async function check() {
        if (!registration) { if (!starting) start(); return; }
        if (checking || applying) return;
        checking = true; status = 'checking'; lastCheck = Date.now();
        try {
            var response = await fetch('release.json?t=' + Date.now(), { cache: 'no-store' });
            if (!response.ok) throw new Error('release');
            var latest = await response.json(); version = latest.version;
            if (latest.build === build) status = 'latest';
            else {
                status = 'installing';
                await registration.update();
                if (registration.waiting) ready(registration.waiting);
                else if (!registration.installing) status = 'error';
            }
        } catch (_) { if (registration.waiting) ready(registration.waiting); else status = navigator.onLine ? 'error' : 'offline'; }
        finally { checking = false; }
    }
    function apply() {
        if (!registration || !registration.waiting || applying) return;
        applying = true; status = 'applying';
        var channel = new MessageChannel();
        var timeout = setTimeout(function () { applying = false; status = 'error'; }, 15000);
        channel.port1.onmessage = function (event) {
            if (event.data === 'blocked') { clearTimeout(timeout); applying = false; status = 'blocked'; }
        };
        registration.waiting.postMessage({ type: 'activate' }, [channel.port2]);
    }
    if ('serviceWorker' in navigator) navigator.serviceWorker.addEventListener('controllerchange', function () {
        if (applying) location.reload();
    });
    window.addEventListener('online', check);
    document.addEventListener('visibilitychange', function () { if (!document.hidden && Date.now() - lastCheck > 60000) check(); });
    setInterval(function () { if (!document.hidden) check(); }, 300000);
    window.rrUpdates = { start: start, check: check, apply: apply, state: function () { return status + '|' + version; } };
})();
