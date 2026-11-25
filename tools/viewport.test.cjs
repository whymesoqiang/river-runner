const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../web/src/commonMain/resources/viewport.js'), 'utf8');
const { measure } = require('../web/src/commonMain/resources/viewport.js');
const env = (width, height) => ({ innerWidth: width, innerHeight: height, phone: true, insets: {} });

test('iPhone portrait home-screen launch shows rotation prompt', () => {
    const r = measure({ ...env(390, 844), insets: { top: 47 } });
    assert.equal(r.rotate, true);
    assert.equal(r.height, 797);
});
test('standalone rotation uses visual viewport even when inner dimensions are stale', () => {
    const r = measure({ ...env(390, 844), visualViewport: { width: 844, height: 390 }, insets: { left: 47 } });
    assert.equal(r.rotate, false);
    assert.equal(r.width, 797);
    assert.equal(r.height, 390);
});
test('visual viewport values are already CSS pixels and must not be scaled twice', () => {
    const r = measure({ ...env(844, 390), visualViewport: { width: 780, height: 1688, scale: 0.5 } });
    assert.equal(r.width, 780);
    assert.equal(r.height, 1688);
});
test('iPad split view remains usable without phone rotation gate', () => {
    assert.equal(measure({ ...env(400, 800), phone: false }).rotate, false);
});
test('portrait -> landscape -> resume updates geometry, mounts once and stops polling', () => {
    let now = 0, seq = 0, callbacks = new Map(), events = {}, attrs = {}, classes = new Set();
    const app = { style: {} };
    const on = (type, callback) => (events[type] ||= []).push(callback);
    const dispatch = (type) => (events[type] || []).forEach(fn => fn({ type }));
    const schedule = (fn, delay) => { callbacks.set(++seq, { fn, at: now + delay }); return seq; };
    const doc = {
        hidden: false, addEventListener: on,
        getElementById: id => id === 'app' ? app : {},
        documentElement: {
            classList: { toggle: (name, enabled) => enabled ? classes.add(name) : classes.delete(name) },
            setAttribute: (k, v) => { attrs[k] = v; }, getAttribute: k => attrs[k], removeAttribute: k => { delete attrs[k]; }
        }
    };
    const w = {
        document: doc, navigator: { userAgent: 'iPhone' }, innerWidth: 390, innerHeight: 844,
        visualViewport: { width: 390, height: 844, addEventListener: on },
        getComputedStyle: () => ({ paddingLeft: '0', paddingRight: '0', paddingTop: '0' }),
        addEventListener: on, dispatchEvent: e => dispatch(e.type), Event: class { constructor(type) { this.type = type; } },
        requestAnimationFrame: fn => schedule(fn, 16), setTimeout: schedule, clearTimeout: id => callbacks.delete(id)
    };
    function advance(ms) {
        const end = now + ms;
        let count = 0;
        while (true) {
            const next = [...callbacks.entries()].sort((a,b) => a[1].at - b[1].at)[0];
            if (!next || next[1].at > end) break;
            assert.ok(count++ < 100, 'viewport must not trigger a resize loop');
            now = next[1].at; callbacks.delete(next[0]); next[1].fn();
        }
        now = end;
    }
    let mounted = false, mounts = 0;
    on('rr-viewport-ready', () => { if (!mounted) { mounted = true; mounts++; } });
    vm.runInNewContext(source, { window: w });
    advance(2000);
    assert.equal(mounts, 0);
    assert.ok(classes.has('rr-rotate'));
    w.visualViewport.width = 844; w.visualViewport.height = 390;
    dispatch('orientationchange'); advance(2000);
    assert.equal(app.style.width, '844px'); assert.equal(app.style.height, '390px');
    assert.equal(mounts, 1); assert.equal(callbacks.size, 0);
    w.visualViewport.width = 390; w.visualViewport.height = 844;
    dispatch('pageshow'); advance(2000); assert.ok(classes.has('rr-rotate'));
    w.visualViewport.width = 844; w.visualViewport.height = 390;
    dispatch('visibilitychange'); advance(2000);
    assert.equal(app.style.width, '844px'); assert.equal(mounts, 1); assert.equal(callbacks.size, 0);
});

test('landscape home indicator and notch leave the full app inside safe bounds', () => {
    const r = measure({ ...env(844, 390), insets: { left: 47, right: 47, bottom: 21 } });
    assert.equal(r.width, 750);
    assert.equal(r.height, 369);
    assert.equal(r.left, 47);
    assert.equal(r.rotate, false);
});
