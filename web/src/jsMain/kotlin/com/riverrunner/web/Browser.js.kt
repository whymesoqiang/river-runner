package com.riverrunner.web

// Each call is wrapped in try/catch: Safari in private mode, or a full storage, throws.

internal actual fun storageGet(key: String): String? =
    js("(function(k){ try { return localStorage.getItem(k); } catch (e) { return null; } })")(key) as String?

internal actual fun storageSet(key: String, value: String): Boolean =
    js("(function(k, v){ try { localStorage.setItem(k, v); return true; } catch (e) { return false; } })")(key, value) as Boolean

internal actual fun storageRemove(key: String) {
    js("(function(k){ try { localStorage.removeItem(k); } catch (e) {} })")(key)
}

internal actual fun browserLanguage(): String = js("navigator.language || ''") as String

internal actual fun playAudio(url: String, volume: Double, rate: Double) {
    js("(function(u, v, r){ try { var a = new Audio(u); a.volume = v; a.playbackRate = r; var p = a.play(); if (p && p.catch) p.catch(function(){}); } catch (e) {} })")(url, volume, rate)
}

internal actual fun workerSubmit(id: Int, payload: String) { js("window.rrCompute.submit")(id, payload) }
internal actual fun workerPoll(id: Int): String? = js("window.rrCompute.poll")(id) as String?
internal actual fun workerCancel(id: Int) { js("window.rrCompute.cancel")(id) }
