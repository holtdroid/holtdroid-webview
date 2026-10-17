package io.github.kriakiku.holtdroid.webview.engine

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import io.github.kriakiku.holtdroid.webview.model.EngineKind
import io.github.kriakiku.holtdroid.webview.model.Profile
import kotlinx.coroutines.flow.StateFlow

data class PageState(
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secure: Boolean = false,
    val certificateError: Boolean = false,
    val favicon: Bitmap? = null,
)

enum class ConsoleLevel { LOG, DEBUG, INFO, WARN, ERROR, RESULT, INPUT }

data class ConsoleEntry(
    val level: ConsoleLevel,
    val message: String,
    val source: String = "",
    val time: Long = System.currentTimeMillis(),
)

enum class ClearScope { CACHE, COOKIES, STORAGE, HISTORY, ALL }

enum class Capability { EVALUATE, CONSOLE, USER_SCRIPT, PROXY, SSL_OVERRIDE, HEADERS, ERUDA }

sealed interface Prompt {
    val id: Long

    data class Certificate(
        override val id: Long,
        val url: String,
        val problem: String,
        val proceed: () -> Unit,
        val cancel: () -> Unit,
    ) : Prompt

    data class Permission(
        override val id: Long,
        val origin: String,
        val resources: List<String>,
        val grant: () -> Unit,
        val deny: () -> Unit,
    ) : Prompt

    data class HttpAuth(
        override val id: Long,
        val host: String,
        val realm: String,
        val proceed: (String, String) -> Unit,
        val cancel: () -> Unit,
    ) : Prompt
}

interface EngineHost {
    fun prompt(prompt: Prompt)
    fun notify(message: String)
    fun requestAndroidPermissions(permissions: List<String>, done: (Boolean) -> Unit)
    fun pickFiles(intent: Intent, done: (Array<Uri>?) -> Unit)
    fun openExternal(intent: Intent): Boolean
    fun enterFullscreen(view: View, exit: () -> Unit)
    fun exitFullscreen()
    fun rendererGone()
    fun nextPromptId(): Long
}

interface BrowserEngine {
    val kind: EngineKind
    val page: StateFlow<PageState>
    val console: StateFlow<List<ConsoleEntry>>
    val capabilities: Set<Capability>
    val view: View
    val debugTarget: String

    fun apply(profile: Profile)
    fun load(url: String)
    fun reload(bypassCache: Boolean = false)
    fun back()
    fun forward()
    fun stop()
    fun setFrozen(frozen: Boolean)
    fun clear(scope: ClearScope, done: () -> Unit)
    fun evaluate(script: String, result: (String) -> Unit)
    fun record(entry: ConsoleEntry)
    fun clearConsole()
    fun resume()
    fun pause()
    fun destroy()
}
