package com.riverrunner.web

// Each call is wrapped in try/catch: Safari in private mode, or a full storage, throws.

private fun jsStorageGet(k: String): String? = js("(function(){ try { return localStorage.getItem(k); } catch (e) { return null; } })()")
private fun jsStorageSet(k: String, v: String): Boolean = js("(function(){ try { localStorage.setItem(k, v); return true; } catch (e) { return false; } })()")
private fun jsStorageRemove(k: String): Unit = js("(function(){ try { localStorage.removeItem(k); } catch (e) {} })()")
private fun jsLanguage(): String = js("navigator.language || ''")
private fun jsPlay(u: String, v: Double, r: Double): Unit =
    js("window.rrPlayAudio(u, v, r)")

internal actual fun storageGet(key: String): String? = jsStorageGet(key)
internal actual fun storageSet(key: String, value: String): Boolean = jsStorageSet(key, value)
internal actual fun storageRemove(key: String) = jsStorageRemove(key)
internal actual fun browserLanguage(): String = jsLanguage()
internal actual fun playAudio(url: String, volume: Double, rate: Double) = jsPlay(url, volume, rate)

internal actual fun workerSubmit(id: Int, payload: String): Unit = js("window.rrCompute.submit(id, payload)")
internal actual fun workerPoll(id: Int): String? = js("window.rrCompute.poll(id)")
internal actual fun workerCancel(id: Int): Unit = js("window.rrCompute.cancel(id)")

internal actual fun webUpdateStatus(): String = js("window.rrUpdates ? window.rrUpdates.state() : 'checking|'")
internal actual fun webCheckUpdates(): Unit = js("window.rrUpdates && window.rrUpdates.check()")
internal actual fun webApplyUpdate(): Unit = js("window.rrUpdates && window.rrUpdates.apply()")
