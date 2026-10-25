package io.github.holtdroid.webview.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.core.net.toUri
import io.github.holtdroid.webview.model.EngineKind
import io.github.holtdroid.webview.model.PermissionMode
import io.github.holtdroid.webview.model.Profile
import io.github.holtdroid.webview.model.UserAgentMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.BuildConfig as GeckoBuildConfig
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController

object GeckoSupport {
    const val AVAILABLE = true
    val VERSION: String get() = GeckoBuildConfig.MOZILLA_VERSION

    private var runtime: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime = runtime ?: GeckoRuntime.create(
        context.applicationContext,
        GeckoRuntimeSettings.Builder()
            .remoteDebuggingEnabled(true)
            .consoleOutput(true)
            .aboutConfigEnabled(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
            .build(),
    ).also { runtime = it }

    fun create(context: Context, host: EngineHost): BrowserEngine? = GeckoEngine(context, host)
}

class GeckoEngine(context: Context, private val host: EngineHost) : BrowserEngine {
    override val kind = EngineKind.GECKO
    override val capabilities = emptySet<Capability>()

    private val runtime = GeckoSupport.runtime(context)
    private val session = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(false).build())
    private val geckoView = GeckoView(context).apply {
        setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
        setSession(this@GeckoEngine.session)
    }
    override val view: View get() = geckoView
    override val debugTarget: String = "${context.packageName}/firefox-debugger-socket"

    private val _page = MutableStateFlow(PageState())
    override val page: StateFlow<PageState> = _page.asStateFlow()
    override val console: StateFlow<List<ConsoleEntry>> = MutableStateFlow(emptyList())

    private var profile = Profile()
    private var frozen = false

    init {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                _page.update { it.copy(url = url, loading = true, progress = 0) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                _page.update { it.copy(loading = false, progress = 100) }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                _page.update { it.copy(progress = progress, loading = progress < 100) }
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                _page.update { it.copy(secure = securityInfo.isSecure, certificateError = securityInfo.isException) }
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                _page.update { it.copy(url = url ?: it.url) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                _page.update { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                _page.update { it.copy(canGoForward = canGoForward) }
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                val uri = request.uri.toUri()
                return when (uri.scheme?.lowercase()) {
                    "http", "https" -> if (profile.allowsHost(uri.host)) {
                        GeckoResult.allow()
                    } else {
                        host.notify("Blocked navigation to ${uri.host}")
                        GeckoResult.deny()
                    }
                    "about", "data", "blob", "javascript", "file", "resource", "moz-extension" -> GeckoResult.allow()
                    else -> {
                        if (profile.externalApps) {
                            val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                            if (!host.openExternal(intent)) host.notify("No app can open ${uri.scheme}: links")
                        } else {
                            host.notify("Blocked ${uri.scheme}: link (external apps are off)")
                        }
                        GeckoResult.deny()
                    }
                }
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (profile.popups) load(uri)
                return null
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                _page.update { it.copy(title = title.orEmpty()) }
            }

            override fun onCrash(session: GeckoSession) {
                host.rendererGone()
            }

            override fun onKill(session: GeckoSession) {
                host.rendererGone()
            }
        }
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                val wanted = permissions?.toList().orEmpty()
                if (wanted.isEmpty()) callback.grant() else host.requestAndroidPermissions(wanted) { if (it) callback.grant() else callback.reject() }
            }

            override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int> {
                val result = GeckoResult<Int>()
                decide(perm.uri, listOf(permissionName(perm.permission)), { result.complete(ContentPermission.VALUE_ALLOW) }) {
                    result.complete(ContentPermission.VALUE_DENY)
                }
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                val resources = listOfNotNull(video?.let { "camera" }, audio?.let { "microphone" })
                decide(uri, resources, {
                    val android = listOfNotNull(video?.let { Manifest.permission.CAMERA }, audio?.let { Manifest.permission.RECORD_AUDIO })
                    host.requestAndroidPermissions(android) { ok ->
                        if (ok) callback.grant(video?.firstOrNull(), audio?.firstOrNull()) else callback.reject()
                    }
                }, callback::reject)
            }
        }
        session.open(runtime)
    }

    private fun permissionName(permission: Int): String = when (permission) {
        GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "location"
        GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notifications"
        GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "persistent storage"
        GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> "autoplay"
        else -> "permission $permission"
    }

    private fun decide(origin: String, resources: List<String>, grant: () -> Unit, deny: () -> Unit) {
        when (profile.permissions) {
            PermissionMode.GRANT -> grant()
            PermissionMode.DENY -> deny()
            PermissionMode.ASK -> host.prompt(Prompt.Permission(host.nextPromptId(), origin, resources, grant, deny))
        }
    }

    override fun apply(profile: Profile) {
        this.profile = profile
        runtime.settings.apply {
            javaScriptEnabled = profile.javaScript
            remoteDebuggingEnabled = profile.debugging
            consoleOutputEnabled = profile.console
            fontSizeFactor = profile.textZoom / 100f
        }
        session.settings.apply {
            allowJavascript = profile.javaScript
            userAgentMode = if (profile.userAgent == UserAgentMode.DESKTOP) {
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            }
            viewportMode = if (profile.userAgent == UserAgentMode.DESKTOP || profile.overviewMode) {
                GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            }
            userAgentOverride = when {
                profile.userAgent == UserAgentMode.CUSTOM && profile.customUserAgent.isNotBlank() -> profile.customUserAgent
                else -> null
            }
        }
    }

    override fun load(url: String) = session.loadUri(url)

    override fun reload(bypassCache: Boolean) {
        session.reload(if (bypassCache) GeckoSession.LOAD_FLAGS_BYPASS_CACHE else GeckoSession.LOAD_FLAGS_NONE)
    }

    override fun back() = session.goBack()
    override fun forward() = session.goForward()
    override fun stop() = session.stop()

    override fun setFrozen(frozen: Boolean) {
        this.frozen = frozen
        session.setActive(!frozen)
    }

    override fun clear(scope: ClearScope, done: () -> Unit) {
        if (scope == ClearScope.HISTORY) {
            session.purgeHistory()
            done()
            return
        }
        val flags = when (scope) {
            ClearScope.CACHE -> StorageController.ClearFlags.ALL_CACHES
            ClearScope.COOKIES -> StorageController.ClearFlags.COOKIES
            ClearScope.STORAGE -> StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.SITE_DATA
            else -> StorageController.ClearFlags.ALL
        }
        if (scope == ClearScope.ALL) session.purgeHistory()
        runtime.storageController.clearData(flags).accept({ done() }, { done() })
    }

    override fun evaluate(script: String, result: (String) -> Unit) = result("evaluate is not supported by GeckoView")

    override fun record(entry: ConsoleEntry) = Unit

    override fun clearConsole() = Unit

    override fun resume() = session.setActive(!frozen)

    override fun pause() = Unit

    override fun destroy() {
        geckoView.releaseSession()
        session.close()
    }
}
