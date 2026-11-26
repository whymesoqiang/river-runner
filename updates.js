/* Only app assets are updated. Never remove localStorage, IndexedDB, or user saves. */
(function () {
    'use strict';
    var build = document.querySelector('meta[name="rr-build"]').content;
    var status = 'checking', version = '', registration = null, starting = null, checking = false, applying = false;
    var lastCheck = 0, progress = '', lastProgress = 0;
    function bounded(promise, ms) {
        var timer;
        return Promise.race([promise, new Promise(function (_, reject) { timer = setTimeout(function () { reject(new Error('timeout')); }, ms); })])
            .finally(function () { clearTimeout(timer); });
    }
    function receiveProgress(data) {
        if (!data || data.type !== 'rr-update-progress' || data.build === build || applying) return;
        version = data.version; progress = data.total > 0 ? data.done + '/' + data.total : '';
        lastProgress = Date.now(); status = 'installing';
    }
    function runtime() {
        return performance.getEntriesByType('resource').some(function (e) { return /originJsRiverRunner\.js/.test(e.name); }) ? 'js' : 'wasm';
    }
    function ready(worker) {
        if (!worker) return;
        var channel = new MessageChannel();
        var timer = setTimeout(function () { channel.port1.close(); status = 'timeout'; }, 8000);
        channel.port1.onmessage = function (event) { clearTimeout(timer); version = event.data.version; status = event.data.build === build ? 'latest' : 'ready'; channel.port1.close(); };
        worker.postMessage({ type: 'version' }, [channel.port2]);
    }
    function watch(reg) {
        if (reg.waiting) ready(reg.waiting);
        function install(worker) {
            if (!worker) return;
            status = 'installing'; lastProgress = Date.now();
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
            registration = await bounded(navigator.serviceWorker.register('sw.js?runtime=' + runtime(), { updateViaCache: 'none' }), 30000);
            watch(registration);
            await check();
        })().catch(function () { status = navigator.onLine ? 'timeout' : 'offline'; starting = null; });
        return starting;
    }
    async function check() {
        if (!registration) { if (!starting) start(); return; }
        if (checking || applying) return;
        checking = true; status = 'checking'; lastCheck = Date.now();
        try {
            var controller = new AbortController();
            var timer = setTimeout(function () { controller.abort(); }, 20000);
            var response = await fetch('release.json?t=' + Date.now(), { cache: 'no-store', signal: controller.signal });
            if (!response.ok) throw new Error('release');
            var latest = await response.json(); clearTimeout(timer); version = latest.version;
            if (latest.build === build) status = 'latest';
            else {
                status = 'installing'; lastProgress = Date.now();
                await bounded(registration.update(), 30000);
                if (registration.waiting) ready(registration.waiting);
                else if (!registration.installing) status = 'error';
                else { var channel = new MessageChannel(); channel.port1.onmessage = function (e) { receiveProgress(e.data); channel.port1.close(); }; registration.installing.postMessage({ type: 'progress' }, [channel.port2]); setTimeout(function () { channel.port1.close(); }, 8000); }
            }
        } catch (_) { if (registration.waiting) ready(registration.waiting); else status = navigator.onLine ? 'timeout' : 'offline'; }
        finally { clearTimeout(timer); checking = false; }
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
    if ('serviceWorker' in navigator) navigator.serviceWorker.addEventListener('message', function (e) { receiveProgress(e.data); });
    setInterval(function () { if (status === 'installing' && Date.now() - lastProgress > 100000) status = navigator.onLine ? 'timeout' : 'offline'; }, 2000);
    window.rrUpdates = { start: start, check: check, apply: apply, state: function () { return status + '|' + version + (status === 'installing' && progress ? '|' + progress : ''); } };
})();
