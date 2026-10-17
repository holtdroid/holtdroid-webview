package io.github.kriakiku.holtdroid.webview.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Web
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.kriakiku.holtdroid.webview.model.DisplayMode
import io.github.kriakiku.holtdroid.webview.model.EngineKind
import io.github.kriakiku.holtdroid.webview.model.PermissionMode
import io.github.kriakiku.holtdroid.webview.model.Profile
import io.github.kriakiku.holtdroid.webview.model.SslMode
import io.github.kriakiku.holtdroid.webview.model.UserAgentMode

const val DOCS_URL = "https://kriakiku.github.io/holtdroid/tools/webview/"
const val RELEASES_URL = "https://github.com/kriakiku/holtdroid-webview/releases"

val PresetIcons: Map<String, ImageVector> = linkedMapOf(
    "web" to Icons.Rounded.Public,
    "bug" to Icons.Rounded.BugReport,
    "kiosk" to Icons.Rounded.Fullscreen,
    "phone" to Icons.Rounded.PhoneAndroid,
    "resize" to Icons.Rounded.AspectRatio,
    "desktop" to Icons.Rounded.DesktopWindows,
    "fresh" to Icons.Rounded.CleaningServices,
    "proxy" to Icons.Rounded.Hub,
    "lock" to Icons.Rounded.Lock,
    "code" to Icons.Rounded.Code,
    "science" to Icons.Rounded.Science,
    "rocket" to Icons.Rounded.RocketLaunch,
    "dark" to Icons.Rounded.DarkMode,
    "star" to Icons.Rounded.Star,
)

fun presetIcon(key: String): ImageVector = PresetIcons[key] ?: Icons.Rounded.Tune

data class Frame(val label: String, val width: Int, val height: Int)

val Frames = listOf(
    Frame("Fill", 0, 0),
    Frame("320 × 568", 320, 568),
    Frame("360 × 640", 360, 640),
    Frame("393 × 852", 393, 852),
    Frame("412 × 915", 412, 915),
    Frame("430 × 932", 430, 932),
    Frame("600 × 960", 600, 960),
    Frame("768 × 1024", 768, 1024),
    Frame("1024 × 768", 1024, 768),
    Frame("1280 × 800", 1280, 800),
)

enum class Section(val title: String, val icon: ImageVector) {
    DISPLAY("Display", Icons.Rounded.Fullscreen),
    PAGE("Page", Icons.Rounded.Web),
    IDENTITY("Identity and network", Icons.Rounded.Fingerprint),
    SECURITY("Security and privacy", Icons.Rounded.Shield),
    DEVTOOLS("DevTools", Icons.Rounded.BugReport),
    ADVANCED("Advanced", Icons.Rounded.Memory),
}

private val webviewOnly = setOf(EngineKind.WEBVIEW)
private val embedded = setOf(EngineKind.WEBVIEW, EngineKind.GECKO)

data class SettingDef(
    val key: String,
    val title: String,
    val description: String,
    val section: Section,
    val engines: Set<EngineKind> = embedded,
    val labels: Map<String, String> = emptyMap(),
    val range: IntRange? = null,
    val step: Int = 1,
    val multiline: Boolean = false,
    val placeholder: String = "",
)

val Catalog = listOf(
    SettingDef(
        "display", "Display mode",
        "Standard keeps the system bars and the toolbar. Immersive hides the system bars. Edge to edge draws the page under transparent bars without a toolbar. Kiosk hides everything.",
        Section.DISPLAY,
        labels = mapOf("standard" to "Standard", "immersive" to "Immersive", "edge" to "Edge", "kiosk" to "Kiosk"),
    ),
    SettingDef("toolbar", "Toolbar position", "Where the address bar sits in Standard and Immersive modes.", Section.DISPLAY, labels = mapOf("top" to "Top", "bottom" to "Bottom")),
    SettingDef("handle", "Floating handle", "A small draggable button that opens the controls when the toolbar is hidden.", Section.DISPLAY),
    SettingDef("lockBack", "Back opens controls", "Back never leaves the page: it opens the control panel instead of navigating.", Section.DISPLAY),
    SettingDef(
        "orientation", "Orientation", "Lock the screen orientation while the page is open.", Section.DISPLAY,
        labels = mapOf("auto" to "Auto", "portrait" to "Portrait", "landscape" to "Landscape", "locked" to "Current"),
    ),
    SettingDef("keepScreenOn", "Keep screen on", "Don't let the device sleep while the page is open.", Section.DISPLAY),
    SettingDef("width", "Viewport size", "Frame the page at a fixed size in CSS pixels (dp). Fill uses the whole screen.", Section.DISPLAY),
    SettingDef("scale", "Frame scale", "Zoom the framed page. 0 fits the frame into the screen.", Section.DISPLAY, range = 0..200, step = 5),

    SettingDef("javaScript", "JavaScript", "Run scripts on pages.", Section.PAGE),
    SettingDef("domStorage", "DOM storage", "localStorage and sessionStorage.", Section.PAGE, webviewOnly),
    SettingDef("zoom", "Pinch to zoom", "Let the user zoom the page.", Section.PAGE, webviewOnly),
    SettingDef("zoomControls", "Zoom buttons", "Show the on-screen + / − zoom buttons.", Section.PAGE, webviewOnly),
    SettingDef("wideViewport", "Wide viewport", "Honour <meta name=viewport>. Off makes the layout width match the view.", Section.PAGE, webviewOnly),
    SettingDef("overviewMode", "Overview mode", "Zoom out so the whole page width fits the screen.", Section.PAGE),
    SettingDef("textZoom", "Text zoom", "Text size in percent.", Section.PAGE, range = 50..250, step = 10),
    SettingDef("darkening", "Algorithmic darkening", "Let the engine darken light-only pages when the app theme is dark.", Section.PAGE, webviewOnly),
    SettingDef("mediaAutoplay", "Media autoplay", "Allow audio and video to play without a tap.", Section.PAGE, webviewOnly),
    SettingDef("blockImages", "Block images", "Don't load images from the network.", Section.PAGE, webviewOnly),
    SettingDef("offline", "Offline", "Block every network load, as if the device had no connection.", Section.PAGE, webviewOnly),
    SettingDef(
        "cacheMode", "HTTP cache", "How the engine uses its HTTP cache.", Section.PAGE, webviewOnly,
        labels = mapOf("default" to "Default", "no-cache" to "No cache", "cache-first" to "Cache first", "cache-only" to "Cache only"),
    ),

    SettingDef(
        "userAgent", "User agent", "Default, a desktop Chrome on Linux, or your own string.", Section.IDENTITY,
        labels = mapOf("default" to "Default", "desktop" to "Desktop", "custom" to "Custom"),
    ),
    SettingDef("customUserAgent", "Custom user agent", "Used when User agent is Custom.", Section.IDENTITY, placeholder = "Mozilla/5.0 …", multiline = true),
    SettingDef("userAgentSuffix", "User agent suffix", "Appended to the user agent, e.g. MyApp/1.2.", Section.IDENTITY, webviewOnly, placeholder = "MyApp/1.0"),
    SettingDef("headers", "Extra headers", "Sent with every page load you start (one Name: value per line). Not sent with subresources.", Section.IDENTITY, webviewOnly, multiline = true, placeholder = "Authorization: Bearer …"),
    SettingDef("proxy", "Proxy", "host:port or scheme://host:port for all WebView traffic. Empty uses the system network.", Section.IDENTITY, webviewOnly, placeholder = "127.0.0.1:8080"),
    SettingDef("proxyBypass", "Proxy bypass", "Hosts that skip the proxy, separated by commas.", Section.IDENTITY, webviewOnly, placeholder = "localhost, *.internal"),

    SettingDef(
        "ssl", "TLS certificate errors", "Strict refuses broken certificates, Ask shows a dialog, Ignore proceeds silently.", Section.SECURITY, webviewOnly,
        labels = mapOf("strict" to "Strict", "ask" to "Ask", "ignore" to "Ignore"),
    ),
    SettingDef(
        "mixedContent", "Mixed content", "Whether https pages may load http resources.", Section.SECURITY, webviewOnly,
        labels = mapOf("never" to "Never", "compatible" to "Compatible", "always" to "Always"),
    ),
    SettingDef("safeBrowsing", "Safe Browsing", "Google's malware and phishing checks.", Section.SECURITY, webviewOnly),
    SettingDef("cookies", "Cookies", "Accept cookies.", Section.SECURITY, webviewOnly),
    SettingDef("thirdPartyCookies", "Third-party cookies", "Accept cookies from other sites in frames.", Section.SECURITY, webviewOnly),
    SettingDef(
        "permissions", "Site permissions", "Camera, microphone and location requests from pages.", Section.SECURITY,
        labels = mapOf("ask" to "Ask", "grant" to "Grant", "deny" to "Deny"),
    ),
    SettingDef("allowedHosts", "Allowed hosts", "Lock navigation to these hosts and their subdomains (comma separated). Empty allows everything.", Section.SECURITY, placeholder = "example.com, *.example.org"),
    SettingDef("externalApps", "Open other apps", "Follow tel:, mailto:, intent: and market: links into other apps.", Section.SECURITY),
    SettingDef("popups", "Popups and new windows", "window.open and target=_blank open in the same view.", Section.SECURITY),
    SettingDef("jsDialogs", "JavaScript dialogs", "alert(), confirm(), prompt() and beforeunload.", Section.SECURITY, webviewOnly),
    SettingDef("longPress", "Long press", "Text selection and the context menu on long press.", Section.SECURITY, webviewOnly),
    SettingDef("fileAccess", "file:// access", "Let the view open file:// URLs.", Section.SECURITY, webviewOnly),
    SettingDef("universalFileAccess", "Universal file access", "Let file:// pages read other files and any origin. Unsafe.", Section.SECURITY, webviewOnly),
    SettingDef("contentAccess", "content:// access", "Let the view open content:// URLs from other apps.", Section.SECURITY, webviewOnly),
    SettingDef("clearOnStart", "Clear data on start", "Wipe cookies, storage, cache and history every time this preset opens.", Section.SECURITY),

    SettingDef("debugging", "Remote debugging", "Expose the page to chrome://inspect (WebView) or about:debugging (GeckoView).", Section.DEVTOOLS),
    SettingDef("eruda", "Eruda", "Inject the Eruda in-page DevTools from jsDelivr on every page.", Section.DEVTOOLS, webviewOnly),
    SettingDef("console", "Capture console", "Keep console messages for the in-app console and adb.", Section.DEVTOOLS),
    SettingDef("userScript", "User script", "JavaScript injected at document start into every frame.", Section.DEVTOOLS, webviewOnly, multiline = true, placeholder = "console.log('hello from holtdroid')"),

    SettingDef("hardwareAcceleration", "Hardware acceleration", "Off renders the page in software, which is slower but helps with GPU glitches.", Section.ADVANCED, webviewOnly),
    SettingDef("offscreenPreRaster", "Pre-raster off screen", "Draw tiles outside the viewport ahead of time; smoother scrolling, more memory.", Section.ADVANCED, webviewOnly),
)

data class Tag(val label: String, val warning: Boolean = false)

fun Profile.tags(): List<Tag> = buildList {
    when (display) {
        DisplayMode.IMMERSIVE -> add(Tag("Immersive"))
        DisplayMode.EDGE -> add(Tag("Edge to edge"))
        DisplayMode.KIOSK -> add(Tag("Kiosk"))
        DisplayMode.STANDARD -> Unit
    }
    if (framed) add(Tag("${if (width > 0) width else "fill"} × ${if (height > 0) height else "fill"}"))
    when (engine) {
        EngineKind.GECKO -> add(Tag("GeckoView"))
        EngineKind.CUSTOM_TABS -> add(Tag("Custom Tab"))
        EngineKind.WEBVIEW -> Unit
    }
    when (ssl) {
        SslMode.IGNORE -> add(Tag("TLS errors ignored", warning = true))
        SslMode.ASK -> add(Tag("TLS: ask"))
        SslMode.STRICT -> Unit
    }
    if (userAgent == UserAgentMode.DESKTOP) add(Tag("Desktop UA"))
    if (userAgent == UserAgentMode.CUSTOM) add(Tag("Custom UA"))
    if (proxy.isNotBlank()) add(Tag("Proxy $proxy"))
    if (allowedHosts.isNotBlank()) add(Tag("Locked to ${allowedHosts.split(',').first().trim()}"))
    if (permissions == PermissionMode.GRANT) add(Tag("Grants permissions", warning = true))
    if (universalFileAccess) add(Tag("File access", warning = true))
    if (eruda) add(Tag("Eruda"))
    if (clearOnStart) add(Tag("Clears data"))
    if (!javaScript) add(Tag("No JavaScript"))
    if (offline) add(Tag("Offline"))
    if (!debugging) add(Tag("DevTools off", warning = true))
}

@Composable
fun rememberClipboard(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Holtdroid WebView", text))
        }
    }
}
