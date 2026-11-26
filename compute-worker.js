/* One reusable worker: cancellation discards obsolete replay computations, not just their UI. */
(function () {
  'use strict';
  var worker = null, active = null, queue = [], results = new Map(), timer = null, paused = false;
  function stop() { clearTimeout(timer); if (worker) worker.terminate(); worker = null; active = null; }
  function fail() {
    if (active) results.set(active.id, '{"error":"Background calculation failed"}');
    stop(); pump();
  }
  function pump() {
    if (paused || active || !queue.length) return;
    active = queue.shift();
    try {
      if (!worker) {
        worker = new Worker(new URL('strategyWorker.js', document.baseURI));
        worker.onmessage = function (e) {
          if (!active || e.data.id !== active.id) return;
          clearTimeout(timer); results.set(active.id, e.data.payload); active = null; pump();
        };
        worker.onerror = fail; worker.onmessageerror = fail;
      }
      worker.postMessage(active);
      timer = setTimeout(fail, 60000);
    } catch (e) { fail(); }
  }
  window.rrCompute = {
    submit: function (id, payload) { queue.push({id:id, payload:payload}); pump(); },
    poll: function (id) { var value = results.get(id); if (value === undefined) return null; results.delete(id); return value; },
    cancel: function (id) {
      results.delete(id); queue = queue.filter(function (q) { return q.id !== id; });
      if (active && active.id === id) { stop(); pump(); }
    }
  };
  window.addEventListener('pagehide', function () { paused = true; if (active) queue.unshift(active); stop(); });
  window.addEventListener('pageshow', function () { paused = false; pump(); });
})();
