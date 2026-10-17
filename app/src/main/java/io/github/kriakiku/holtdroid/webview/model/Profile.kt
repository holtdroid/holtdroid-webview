package io.github.kriakiku.holtdroid.webview.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EngineKind {
    @SerialName("webview") WEBVIEW,
    @SerialName("gecko") GECKO,
    @SerialName("custom-tabs") CUSTOM_TABS,
}

@Serializable
enum class DisplayMode {
    @SerialName("standard") STANDARD,
    @SerialName("immersive") IMMERSIVE,
    @SerialName("edge") EDGE,
    @SerialName("kiosk") KIOSK,
}

@Serializable
enum class ToolbarPosition {
    @SerialName("top") TOP,
    @SerialName("bottom") BOTTOM,
}

@Serializable
enum class Orientation {
    @SerialName("auto") AUTO,
    @SerialName("portrait") PORTRAIT,
    @SerialName("landscape") LANDSCAPE,
    @SerialName("locked") LOCKED,
}

@Serializable
enum class SslMode {
    @SerialName("strict") STRICT,
    @SerialName("ask") ASK,
    @SerialName("ignore") IGNORE,
}

@Serializable
enum class MixedContent {
    @SerialName("never") NEVER,
    @SerialName("compatible") COMPATIBLE,
    @SerialName("always") ALWAYS,
}

@Serializable
enum class CacheMode {
    @SerialName("default") DEFAULT,
    @SerialName("no-cache") NO_CACHE,
    @SerialName("cache-first") CACHE_FIRST,
    @SerialName("cache-only") CACHE_ONLY,
}

@Serializable
enum class PermissionMode {
    @SerialName("ask") ASK,
    @SerialName("grant") GRANT,
    @SerialName("deny") DENY,
}

@Serializable
enum class UserAgentMode {
    @SerialName("default") DEFAULT,
    @SerialName("desktop") DESKTOP,
    @SerialName("custom") CUSTOM,
}

@Serializable
data class Profile(
    val url: String = "https://www.google.com/",
    val engine: EngineKind = EngineKind.WEBVIEW,

    val display: DisplayMode = DisplayMode.STANDARD,
    val toolbar: ToolbarPosition = ToolbarPosition.TOP,
    val handle: Boolean = true,
    val lockBack: Boolean = false,
    val orientation: Orientation = Orientation.AUTO,
    val keepScreenOn: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val scale: Int = 0,

    val javaScript: Boolean = true,
    val domStorage: Boolean = true,
    val zoom: Boolean = true,
    val zoomControls: Boolean = false,
    val wideViewport: Boolean = true,
    val overviewMode: Boolean = false,
    val textZoom: Int = 100,
    val darkening: Boolean = false,
    val mediaAutoplay: Boolean = false,
    val blockImages: Boolean = false,
    val offline: Boolean = false,
    val cacheMode: CacheMode = CacheMode.DEFAULT,

    val userAgent: UserAgentMode = UserAgentMode.DEFAULT,
    val customUserAgent: String = "",
    val userAgentSuffix: String = "",
    val headers: String = "",

    val ssl: SslMode = SslMode.STRICT,
    val mixedContent: MixedContent = MixedContent.COMPATIBLE,
    val safeBrowsing: Boolean = true,
    val cookies: Boolean = true,
    val thirdPartyCookies: Boolean = true,
    val permissions: PermissionMode = PermissionMode.ASK,
    val allowedHosts: String = "",
    val externalApps: Boolean = true,
    val popups: Boolean = false,
    val jsDialogs: Boolean = true,
    val longPress: Boolean = true,
    val fileAccess: Boolean = false,
    val universalFileAccess: Boolean = false,
    val contentAccess: Boolean = true,
    val clearOnStart: Boolean = false,

    val debugging: Boolean = true,
    val eruda: Boolean = false,
    val console: Boolean = true,
    val userScript: String = "",
    val proxy: String = "",
    val proxyBypass: String = "",

    val hardwareAcceleration: Boolean = true,
    val offscreenPreRaster: Boolean = false,
) {
    val framed: Boolean get() = width > 0 || height > 0

    val showsToolbar: Boolean get() = display == DisplayMode.STANDARD || display == DisplayMode.IMMERSIVE

    val hidesSystemBars: Boolean get() = display == DisplayMode.IMMERSIVE || display == DisplayMode.KIOSK

    fun headerMap(): Map<String, String> = headers.lines()
        .mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
        .filter { it.first.isNotEmpty() }
        .toMap()

    fun allowsHost(host: String?): Boolean {
        val patterns = allowedHosts.split(',', ' ', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (patterns.isEmpty() || patterns.contains("*")) return true
        val h = host?.lowercase() ?: return false
        return patterns.any { p ->
            val base = p.removePrefix("*.")
            h == base || h.endsWith(".$base")
        }
    }
}

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "web",
    val profile: Profile = Profile(),
)

@Serializable
enum class AppTheme {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
}

@Serializable
data class AppState(
    val presets: List<Preset> = DefaultPresets.all,
    val defaultPreset: String = DefaultPresets.all.first().id,
    val recents: List<String> = emptyList(),
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColor: Boolean = false,
    val externalControl: Boolean = true,
) {
    fun preset(ref: String?): Preset? {
        if (ref.isNullOrBlank()) return null
        return presets.firstOrNull { it.id == ref } ?: presets.firstOrNull { it.name.equals(ref, ignoreCase = true) }
    }

    val default: Preset get() = preset(defaultPreset) ?: presets.firstOrNull() ?: DefaultPresets.all.first()
}

fun desktopUserAgent(mobile: String): String {
    val desktop = mobile
        .replace(Regex("""\(Linux; Android [^)]*\)"""), "(X11; Linux x86_64)")
        .replace(" Mobile", "")
    return desktop.replace(Regex("""Version/[\d.]+ """), "")
}
