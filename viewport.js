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
        var bottom = insets.bottom || 0;
        return {
            left: Math.round(left + (vv ? vv.offsetLeft || 0 : 0)), top: Math.round(top + (vv ? vv.offsetTop || 0 : 0)),
            width: Math.max(1, Math.round(env.phone && height > width ? height - top - bottom : width - left - right)),
            height: Math.max(1, Math.round(env.phone && height > width ? width - left - right : height - top - bottom)),
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
            insets: { left: parseFloat(cs.paddingLeft), right: parseFloat(cs.paddingRight), top: parseFloat(cs.paddingTop), bottom: parseFloat(cs.paddingBottom) }
        });
        doc.documentElement.classList.toggle('rr-landscape', rect.rotate);
        root.__rrRotated = rect.rotate;
        var size = [rect.left, rect.top, rect.width, rect.height, rect.rotate].join(':');
        var changed = size !== lastSize;
        if (changed) {
            lastSize = size;
            app.style.transformOrigin = '0 0';
            app.style.transform = rect.rotate ? 'rotate(90deg)' : 'none';
            app.style.left = (rect.left + (rect.rotate ? rect.height : 0)) + 'px'; app.style.top = rect.top + 'px';
            app.style.width = rect.width + 'px'; app.style.height = rect.height + 'px';
            // Compose listens for resize too. Only notify on actual geometry changes,
            // otherwise our resize listener would make an endless animation loop.
            root.dispatchEvent(new root.Event('resize'));
        }
        if (changed) {
            root.clearTimeout(readyTimer);
            readyTimer = 0;
            doc.documentElement.removeAttribute('data-rr-viewport-ready');
        }
        if (!readyTimer && doc.documentElement.getAttribute('data-rr-viewport-ready') !== 'true') {
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
    // Compose mouse input uses offsetX/Y (already transformed by the browser),
    // but its touch adapter subtracts the canvas bounding rect from clientX/Y.
    // Supply the corresponding canvas-local coordinates for native touch input.
    function mapTouches(event) {
        if (!root.__rrRotated || !event.target || !/^(DIV|CANVAS)$/.test(event.target.tagName)) return;
        var rect = app.getBoundingClientRect();
        ['touches', 'targetTouches', 'changedTouches'].forEach(function (key) {
            var list = Array.prototype.map.call(event[key], function (touch) {
                return {
                    identifier: touch.identifier, target: touch.target,
                    clientX: rect.left + touch.clientY - rect.top,
                    clientY: rect.top + rect.width - (touch.clientX - rect.left),
                    pageX: touch.pageX, pageY: touch.pageY, screenX: touch.screenX, screenY: touch.screenY,
                    radiusX: touch.radiusY, radiusY: touch.radiusX, rotationAngle: touch.rotationAngle, force: touch.force
                };
            });
            list.item = function (index) { return list[index] || null; };
            Object.defineProperty(event, key, { value: list });
        });
    }
    ['touchstart', 'touchmove', 'touchend', 'touchcancel'].forEach(function (type) {
        app.addEventListener(type, mapTouches, { capture: true, passive: true });
    });
    fit();
    settle();
})(typeof window === 'undefined' ? null : window);
