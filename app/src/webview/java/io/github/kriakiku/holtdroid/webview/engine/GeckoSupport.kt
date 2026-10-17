package io.github.kriakiku.holtdroid.webview.engine

import android.content.Context

object GeckoSupport {
    const val AVAILABLE = false
    const val VERSION = ""

    fun create(context: Context, host: EngineHost): BrowserEngine? = null
}
