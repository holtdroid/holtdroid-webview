package io.github.kriakiku.holtdroid.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.MutableContextWrapper
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kriakiku.holtdroid.webview.engine.BrowserEngine
import io.github.kriakiku.holtdroid.webview.engine.ClearScope
import io.github.kriakiku.holtdroid.webview.engine.EngineHost
import io.github.kriakiku.holtdroid.webview.engine.GeckoSupport
import io.github.kriakiku.holtdroid.webview.engine.Prompt
import io.github.kriakiku.holtdroid.webview.engine.WebViewEngine
import io.github.kriakiku.holtdroid.webview.model.AppState
import io.github.kriakiku.holtdroid.webview.model.AppTheme
import io.github.kriakiku.holtdroid.webview.model.DefaultPresets
import io.github.kriakiku.holtdroid.webview.model.EngineKind
import io.github.kriakiku.holtdroid.webview.model.Preset
import io.github.kriakiku.holtdroid.webview.model.Profile
import io.github.kriakiku.holtdroid.webview.model.Store
import io.github.kriakiku.holtdroid.webview.model.override
import io.github.kriakiku.holtdroid.webview.model.expandSize
import io.github.kriakiku.holtdroid.webview.model.normalizeUrl
import io.github.kriakiku.holtdroid.webview.model.parseBoolean
import io.github.kriakiku.holtdroid.webview.model.toJson
import io.github.kriakiku.holtdroid.webview.ui.Keyboard
import io.github.kriakiku.holtdroid.webview.ui.KeyboardResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

sealed interface Screen {
    data object Home : Screen
    data class Editor(val presetId: String?, val live: Boolean) : Screen
    data object Browser : Screen
}

data class Session(
    val preset: Preset,
    val profile: Profile,
    val engine: BrowserEngine,
    val frozen: Boolean = false,
)

data class Viewport(val width: Int, val height: Int, val scale: Int)

class Fullscreen(val view: View, val exit: () -> Unit)

interface ActivityBridge {
    fun askPermissions(permissions: List<String>, done: (Boolean) -> Unit)
    fun pickFiles(intent: Intent, done: (Array<Uri>?) -> Unit)
    fun startExternal(intent: Intent): Boolean
    fun openCustomTab(url: String)
    fun keyboard(show: Boolean?): KeyboardResult
}

class Reply(val ok: Boolean, val message: String)

@SuppressLint("StaticFieldLeak")
class AppViewModel(app: Application) : AndroidViewModel(app), EngineHost {
    val store = Store(app, viewModelScope)
    val state: StateFlow<AppState> = store.state

    private val context = MutableContextWrapper(app)
    private var bridge: ActivityBridge? = null

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _prompts = MutableStateFlow<List<Prompt>>(emptyList())
    val prompts: StateFlow<List<Prompt>> = _prompts.asStateFlow()

    private val _fullscreen = MutableStateFlow<Fullscreen?>(null)
    val fullscreen: StateFlow<Fullscreen?> = _fullscreen.asStateFlow()

    val viewport = MutableStateFlow<Viewport?>(null)
    val draft = MutableStateFlow(Preset(id = "", name = ""))

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val promptIds = AtomicLong()

    fun bind(activity: Activity, bridge: ActivityBridge) {
        context.baseContext = activity
        this.bridge = bridge
        _session.value?.engine?.view?.dispatchConfigurationChanged(activity.resources.configuration)
    }

    fun unbind(activity: Activity) {
        if (context.baseContext === activity) {
            context.baseContext = getApplication()
            bridge = null
        }
    }

    fun open(preset: Preset, overrides: Map<String, String> = emptyMap(), url: String? = null): Reply {
        val result = preset.profile.override(overrides)
        val profile = if (url.isNullOrBlank()) result.profile else result.profile.copy(url = normalizeUrl(url))
        if (profile.engine == EngineKind.CUSTOM_TABS) {
            closeSession()
            bridge?.openCustomTab(profile.url) ?: return Reply(false, "no activity to open a custom tab from")
            remember(profile.url)
            return Reply(result.errors.isEmpty(), describe("opened ${profile.url} in a custom tab", result.errors))
        }
        closeSession(goHome = false)
        val engine = createEngine(profile.engine)
        engine.apply(profile)
        _session.value = Session(preset, profile, engine)
        _screen.value = Screen.Browser
        if (profile.clearOnStart) engine.clear(ClearScope.ALL) { engine.load(profile.url) } else engine.load(profile.url)
        remember(profile.url)
        return Reply(result.errors.isEmpty(), describe("opened ${profile.url} with ${preset.name}", result.errors))
    }

    private fun createEngine(kind: EngineKind): BrowserEngine = when (kind) {
        EngineKind.GECKO -> GeckoSupport.create(context, this) ?: run {
            notify("This build has no GeckoView, using WebView")
            WebViewEngine(context, this)
        }
        else -> WebViewEngine(context, this)
    }

    fun update(change: (Profile) -> Profile) {
        val current = _session.value ?: return
        val profile = change(current.profile)
        if (profile.engine != current.profile.engine) {
            if (profile.engine == EngineKind.CUSTOM_TABS) {
                bridge?.openCustomTab(current.engine.page.value.url.ifBlank { profile.url })
                return
            }
            val url = current.engine.page.value.url.ifBlank { profile.url }
            current.engine.destroy()
            val engine = createEngine(profile.engine)
            engine.apply(profile)
            _session.value = current.copy(profile = profile, engine = engine, frozen = false)
            engine.load(url)
            return
        }
        current.engine.apply(profile)
        _session.value = current.copy(profile = profile)
    }

    fun set(values: Map<String, String>): Reply {
        val current = _session.value ?: return Reply(false, "no page is open, use cmd=open")
        val expanded = expandSize(values)
        val url = expanded["url"]
        val result = current.profile.override(expanded - "url")
        update { result.profile }
        if (!url.isNullOrBlank()) load(url)
        val what = (result.applied + listOfNotNull(url?.let { "url" })).joinToString().ifEmpty { "nothing" }
        return Reply(result.errors.isEmpty(), describe("set $what", result.errors))
    }

    fun load(url: String) {
        val current = _session.value ?: return
        val target = normalizeUrl(url)
        current.engine.load(target)
        remember(target)
    }

    fun freeze(frozen: Boolean) {
        val current = _session.value ?: return
        current.engine.setFrozen(frozen)
        _session.value = current.copy(frozen = frozen)
    }

    fun clear(scope: ClearScope, done: () -> Unit = {}) {
        val current = _session.value
        if (current == null) {
            WebViewEngine(context, this).apply { clear(scope) { destroy(); done() } }
            return
        }
        current.engine.clear(scope) {
            notify("Cleared ${scope.name.lowercase()}")
            done()
        }
    }

    fun evaluate(script: String, result: (String) -> Unit = {}) {
        val current = _session.value ?: return result("no page is open")
        current.engine.evaluate(script, result)
    }

    fun closeSession(goHome: Boolean = true) {
        _fullscreen.value?.exit?.invoke()
        _fullscreen.value = null
        _session.value?.engine?.destroy()
        _session.value = null
        _prompts.value = emptyList()
        viewport.value = null
        if (goHome) _screen.value = Screen.Home
    }

    fun goHome() = closeSession()

    fun edit(preset: Preset?) {
        draft.value = preset ?: Preset(id = "", name = "", profile = state.value.default.profile)
        _screen.value = Screen.Editor(preset?.id, live = false)
    }

    fun editLive() {
        val current = _session.value ?: return
        draft.value = current.preset.copy(profile = current.profile)
        _screen.value = Screen.Editor(current.preset.id, live = true)
    }

    fun closeEditor() {
        _screen.value = if ((_screen.value as? Screen.Editor)?.live == true && _session.value != null) Screen.Browser else Screen.Home
    }

    fun applyDraftLive() {
        val profile = draft.value.profile
        update { profile }
        _session.update { it?.copy(preset = it.preset.copy(name = draft.value.name.ifBlank { it.preset.name })) }
        _screen.value = Screen.Browser
    }

    fun savePreset(preset: Preset): Preset {
        val saved = if (preset.id.isBlank()) preset.copy(id = UUID.randomUUID().toString().take(8)) else preset
        val named = saved.copy(name = saved.name.ifBlank { "Preset" })
        store.update { s ->
            val exists = s.presets.any { it.id == named.id }
            s.copy(presets = if (exists) s.presets.map { if (it.id == named.id) named else it } else s.presets + named)
        }
        return named
    }

    fun duplicate(preset: Preset) {
        savePreset(preset.copy(id = "", name = "${preset.name} copy"))
    }

    fun delete(preset: Preset) {
        store.update { s ->
            val left = s.presets.filterNot { it.id == preset.id }
            s.copy(presets = left, defaultPreset = if (s.defaultPreset == preset.id) left.firstOrNull()?.id.orEmpty() else s.defaultPreset)
        }
    }

    fun move(preset: Preset, delta: Int) {
        store.update { s ->
            val list = s.presets.toMutableList()
            val i = list.indexOfFirst { it.id == preset.id }
            val j = (i + delta).coerceIn(0, list.lastIndex)
            if (i < 0 || i == j) return@update s
            list.add(j, list.removeAt(i))
            s.copy(presets = list)
        }
    }

    fun setDefault(preset: Preset) = store.update { it.copy(defaultPreset = preset.id) }

    fun restoreDefaults() = store.update { s ->
        val missing = DefaultPresets.all.filter { d -> s.presets.none { it.id == d.id } }
        s.copy(presets = s.presets.map { p -> DefaultPresets.all.firstOrNull { it.id == p.id } ?: p } + missing)
    }

    fun forgetRecents() = store.update { it.copy(recents = emptyList()) }

    fun setTheme(theme: AppTheme) {
        store.update { it.copy(theme = theme) }
        applyTheme(theme)
    }

    fun setDynamicColor(enabled: Boolean) = store.update { it.copy(dynamicColor = enabled) }

    fun setExternalControl(enabled: Boolean) = store.update { it.copy(externalControl = enabled) }

    private fun remember(url: String) {
        store.update { s -> s.copy(recents = (listOf(url) + s.recents.filterNot { it == url }).take(12)) }
    }

    fun resolve(prompt: Prompt) {
        _prompts.update { list -> list.filterNot { it.id == prompt.id } }
    }

    override fun prompt(prompt: Prompt) {
        _prompts.update { it + prompt }
    }

    override fun notify(message: String) {
        messageChannel.trySend(message)
    }

    override fun requestAndroidPermissions(permissions: List<String>, done: (Boolean) -> Unit) {
        bridge?.askPermissions(permissions, done) ?: done(false)
    }

    override fun pickFiles(intent: Intent, done: (Array<Uri>?) -> Unit) {
        bridge?.pickFiles(intent, done) ?: done(null)
    }

    override fun openExternal(intent: Intent): Boolean = bridge?.startExternal(intent) ?: false

    override fun enterFullscreen(view: View, exit: () -> Unit) {
        _fullscreen.value = Fullscreen(view, exit)
    }

    override fun exitFullscreen() {
        _fullscreen.value = null
    }

    fun leaveFullscreen() {
        _fullscreen.value?.exit?.invoke()
        _fullscreen.value = null
    }

    override fun rendererGone() {
        val current = _session.value ?: return
        val url = current.engine.page.value.url.ifBlank { current.profile.url }
        runCatching { current.engine.destroy() }
        val engine = createEngine(current.profile.engine)
        engine.apply(current.profile)
        _session.value = current.copy(engine = engine, frozen = false)
        engine.load(url)
        notify("The renderer crashed, the page was reloaded")
    }

    override fun nextPromptId(): Long = promptIds.incrementAndGet()

    fun handle(args: Map<String, String>, fromStart: Boolean, reply: (Reply) -> Unit) {
        if (!state.value.externalControl && args.keys.any { it != "url" }) {
            reply(Reply(false, "external control is turned off in the app"))
            return
        }
        val session = _session.value
        val cmd = args["cmd"]?.trim()?.lowercase() ?: when {
            args.containsKey("preset") -> "open"
            session == null -> "open"
            fromStart && args.keys == setOf("url") -> "open"
            else -> "set"
        }
        val rest = args - "cmd"
        val result: Reply = when (cmd) {
            "open" -> {
                val ref = rest["preset"]
                val preset = if (ref == null) state.value.default else state.value.preset(ref)
                if (preset == null) Reply(false, "no preset \"$ref\", try cmd=presets") else open(preset, expandSize(rest - "preset" - "url"), rest["url"])
            }
            "set" -> set(rest)
            "load" -> rest["url"]?.let { load(it); Reply(session != null, if (session != null) "loading $it" else "no page is open") }
                ?: Reply(false, "load needs url")
            "resize" -> {
                val values = expandSize(rest).ifEmpty { mapOf("width" to "0", "height" to "0") }
                set(values.filterKeys { it in setOf("width", "height", "scale") })
            }
            "rotate" -> session?.let { s -> update { it.copy(width = s.profile.height, height = s.profile.width) }; Reply(true, "rotated") }
                ?: Reply(false, "no page is open")
            "freeze", "unfreeze" -> session?.let { freeze(cmd == "freeze"); Reply(true, if (cmd == "freeze") "frozen" else "unfrozen") } ?: Reply(false, "no page is open")
            "toggle-freeze" -> session?.let { freeze(!it.frozen); Reply(true, if (it.frozen) "unfrozen" else "frozen") } ?: Reply(false, "no page is open")
            "reload" -> session?.let { it.engine.reload(rest["hard"]?.let(::parseBoolean) == true); Reply(true, "reloading") }
                ?: Reply(false, "no page is open")
            "back" -> session?.let { it.engine.back(); Reply(true, "back") } ?: Reply(false, "no page is open")
            "forward" -> session?.let { it.engine.forward(); Reply(true, "forward") } ?: Reply(false, "no page is open")
            "stop" -> session?.let { it.engine.stop(); Reply(true, "stopped") } ?: Reply(false, "no page is open")
            "home", "close" -> {
                goHome()
                Reply(true, "closed the page")
            }
            "clear" -> {
                val scope = ClearScope.entries.firstOrNull { it.name.equals(rest["what"] ?: "all", ignoreCase = true) }
                if (scope == null) {
                    Reply(false, "what must be cache | cookies | storage | history | all")
                } else {
                    clear(scope) { reply(Reply(true, "cleared ${scope.name.lowercase()}")) }
                    return
                }
            }
            "eval" -> {
                val js = rest["js"]
                if (js == null || session == null) {
                    Reply(false, if (js == null) "eval needs js" else "no page is open")
                } else {
                    evaluate(js) { reply(Reply(true, it)) }
                    return
                }
            }
            "theme" -> {
                val theme = AppTheme.entries.firstOrNull { it.name.equals(rest["theme"] ?: rest["value"], ignoreCase = true) }
                if (theme == null) Reply(false, "theme must be system | light | dark") else { setTheme(theme); Reply(true, "theme ${theme.name.lowercase()}") }
            }
            "keyboard" -> when (bridge?.keyboard(rest["show"]?.let(::parseBoolean))) {
                KeyboardResult.SHOWN -> Reply(true, "keyboard shown")
                KeyboardResult.HIDDEN -> Reply(true, "keyboard hidden")
                KeyboardResult.BLOCKED -> Reply(
                    false,
                    "a hardware keyboard hides the on-screen one: run `${Keyboard.ALLOW_COMMAND}` " +
                        "or `${Keyboard.grantCommand(getApplication())}`",
                )
                null -> Reply(false, "the app is not in the foreground")
            }
            "console" -> Reply(true, session?.engine?.console?.value.orEmpty().takeLast(rest["lines"]?.toIntOrNull() ?: 50)
                .joinToString("\n") { "${it.level.name.lowercase()}: ${it.message}" })
            "state" -> Reply(true, stateJson())
            "presets" -> Reply(true, state.value.presets.joinToString("\n") { "${it.id}\t${it.name}" })
            else -> Reply(false, "unknown cmd \"$cmd\"")
        }
        reply(result)
    }

    fun stateJson(): String {
        val s = _session.value
        return buildJsonObject {
            put("screen", if (s == null) "home" else "browser")
            if (s != null) {
                val page = s.engine.page.value
                put("preset", s.preset.name)
                put("engine", s.engine.kind.name.lowercase())
                put("url", page.url)
                put("title", page.title)
                put("loading", page.loading)
                put("progress", page.progress)
                put("frozen", s.frozen)
                put("debugSocket", s.engine.debugTarget)
                viewport.value?.let { v ->
                    put("viewport", buildJsonObject { put("width", v.width); put("height", v.height); put("scale", v.scale) })
                }
                put("settings", s.profile.toJson())
            }
            put("presets", JsonArray(state.value.presets.map { JsonPrimitive(it.name) }))
        }.toString()
    }

    override fun onCleared() {
        _session.value?.engine?.destroy()
        _session.value = null
    }

    companion object {
        fun applyTheme(theme: AppTheme) {
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                },
            )
        }
    }
}

private fun describe(message: String, errors: List<String>): String =
    if (errors.isEmpty()) message else "$message; ignored: ${errors.joinToString("; ")}"
