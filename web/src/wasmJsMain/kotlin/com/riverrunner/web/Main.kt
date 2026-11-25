package com.riverrunner.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

/** Progress line on the loading screen (see index.html). */
private fun rrLog(msg: String): Unit = js("window.__rrLog && window.__rrLog(msg)")

/** Removes the loading screen once the app has drawn its first frame. */
private fun rrReady(): Unit = js("window.__rrReady && window.__rrReady()")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    rrLog("应用代码已启动（Wasm）")
    var mounted = false
    fun mountWhenReady() {
        if (mounted || document.documentElement?.getAttribute("data-rr-viewport-ready") != "true") return
        mounted = true
        try {
            ComposeViewport(document.getElementById("app")!!) { WebApp(onFirstFrame = ::rrReady) }
            rrLog("界面已创建，等待第一帧…")
        } catch (e: Throwable) {
            rrLog("启动失败: " + (e.message ?: e.toString()))
        }
    }
    // A portrait launch must not initialize Compose with a miniature landscape layout.
    // The callback also covers rotating while the application code is still loading.
    window.addEventListener("rr-viewport-ready", { mountWhenReady() })
    mountWhenReady()
}
