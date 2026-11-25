/* iOS standalone viewports may settle after pageshow/orientationchange. Never cache
 * the launch dimensions, change viewport meta tags, or infer app size from screen.*.
 * Exposed as a small pure measurement function for regression tests. */
(function (root) {
    function measure(env) {
        var vv = env.visualViewport;
        var width = vv && vv.width > 0 ? vv.width : env.innerWidth;
        var height = vv && vv.height > 0 ? vv.height : env.innerHeight;
        var insets = env.insets;
        var left = Math.min(insets.left || 0, 50);
        var right = Math.min(insets.right || 0, 50);
        var top = insets.top || 0;
        return {
            left: Math.round(left + (vv ? vv.offsetLeft || 0 : 0)), top: Math.round(top + (vv ? vv.offsetTop || 0 : 0)),
            width: Math.max(1, Math.round(width - left - right)),
            height: Math.max(1, Math.round(height - top)),
            // iPad split view remains usable; an iPhone should never show a miniature strip.
            rotate: env.phone && height > width
        };
    }
    if (typeof module === 'object' && module.exports) module.exports = { measure: measure };
    if (!root || !root.document) return;
    var doc = root.document;
    var app = doc.getElementById('app');
    var safe = doc.getElementById('safe');
    var phone = /iPhone|iPod/.test(root.navigator.userAgent) ||
        (/Android/.test(root.navigator.userAgent) && /Mobile/.test(root.navigator.userAgent));
    var frame = 0, readyTimer = 0, settleTimers = [], lastSize = '';
    function fit() {
        frame = 0;
        var cs = root.getComputedStyle(safe);
        var rect = measure({
            visualViewport: root.visualViewport,
            innerWidth: root.innerWidth, innerHeight: root.innerHeight, phone: phone,
            insets: { left: parseFloat(cs.paddingLeft), right: parseFloat(cs.paddingRight), top: parseFloat(cs.paddingTop) }
        });
        doc.documentElement.classList.toggle('rr-rotate', rect.rotate);
        var size = [rect.left, rect.top, rect.width, rect.height].join(':');
        var changed = size !== lastSize;
        if (changed) {
            lastSize = size;
            app.style.left = rect.left + 'px'; app.style.top = rect.top + 'px';
            app.style.width = rect.width + 'px'; app.style.height = rect.height + 'px';
            // Compose listens for resize too. Only notify on actual geometry changes,
            // otherwise our resize listener would make an endless animation loop.
            root.dispatchEvent(new root.Event('resize'));
        }
        if (rect.rotate || changed) {
            root.clearTimeout(readyTimer);
            readyTimer = 0;
            doc.documentElement.removeAttribute('data-rr-viewport-ready');
        }
        if (!rect.rotate && !readyTimer && doc.documentElement.getAttribute('data-rr-viewport-ready') !== 'true') {
            readyTimer = root.setTimeout(function () {
                readyTimer = 0;
                doc.documentElement.setAttribute('data-rr-viewport-ready', 'true');
                root.dispatchEvent(new root.Event('rr-viewport-ready'));
            }, 120);
        }
    }
    function schedule() { if (!frame) frame = root.requestAnimationFrame(fit); }
    function settle() {
        settleTimers.forEach(root.clearTimeout);
        schedule();
        // WebKit sometimes publishes the old viewport for several frames after rotation
        // or restoring a home-screen app. Bounded retries; no permanent polling.
        settleTimers = [120, 300, 800, 1600].map(function (ms) { return root.setTimeout(schedule, ms); });
    }
    if (root.visualViewport) {
        root.visualViewport.addEventListener('resize', schedule);
        root.visualViewport.addEventListener('scroll', schedule);
    }
    root.addEventListener('resize', schedule);
    root.addEventListener('orientationchange', settle);
    root.addEventListener('pageshow', settle);
    doc.addEventListener('visibilitychange', function () { if (!doc.hidden) settle(); });
    ['gesturestart', 'gesturechange', 'gestureend', 'dblclick'].forEach(function (type) {
        doc.addEventListener(type, function (event) { event.preventDefault(); }, { passive: false });
    });
    fit();
    settle();
})(typeof window === 'undefined' ? null : window);
