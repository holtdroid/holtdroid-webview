package io.github.holtdroid.webview.engine

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Environment
import android.os.Message
import android.os.Process
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.holtdroid.webview.model.CacheMode
import io.github.holtdroid.webview.model.EngineKind
import io.github.holtdroid.webview.model.MixedContent
import io.github.holtdroid.webview.model.PermissionMode
import io.github.holtdroid.webview.model.Profile
import io.github.holtdroid.webview.model.SslMode
import io.github.holtdroid.webview.model.UserAgentMode
import io.github.holtdroid.webview.model.desktopUserAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executor


private const val CONSOLE_LIMIT = 1000

@SuppressLint("SetJavaScriptEnabled")
class WebViewEngine(context: Context, private val host: EngineHost) : BrowserEngine {
    override val kind = EngineKind.WEBVIEW
    override val capabilities = Capability.entries.toSet()

    private val webView = WebView(context)
    override val view: View get() = webView
    override val debugTarget: String get() = "webview_devtools_remote_${Process.myPid()}"

    private val _page = MutableStateFlow(PageState())
    override val page: StateFlow<PageState> = _page.asStateFlow()
    private val _console = MutableStateFlow<List<ConsoleEntry>>(emptyList())
    override val console: StateFlow<List<ConsoleEntry>> = _console.asStateFlow()

    private var profile = Profile()
    private var applied: Profile? = null
    private var frozen = false
    private var scriptHandler: ScriptHandler? = null
    private var scriptSource: String? = null
    private val defaultUserAgent: String = WebSettings.getDefaultUserAgent(context)
    private val eruda: String by lazy {
        val source = context.assets.open("eruda.js").bufferedReader().use { it.readText() }
        "(function(){if(window.__hwvEruda)return;window.__hwvEruda=1;" +
            "(function(define,module,exports){\n$source\n}).call(window);window.eruda&&window.eruda.init()})();"
    }

    init {
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.setDownloadListener { url, userAgent, disposition, mimeType, _ -> download(url, userAgent, disposition, mimeType) }
        CookieManager.getInstance().setAcceptCookie(true)
    }

    override fun apply(profile: Profile) {
        val previous = applied
        this.profile = profile
        with(webView.settings) {
            javaScriptEnabled = profile.javaScript
            domStorageEnabled = profile.domStorage
            setSupportZoom(profile.zoom)
            builtInZoomControls = profile.zoom
            displayZoomControls = profile.zoomControls
            useWideViewPort = profile.wideViewport
            loadWithOverviewMode = profile.overviewMode
            textZoom = profile.textZoom
            mediaPlaybackRequiresUserGesture = !profile.mediaAutoplay
            blockNetworkImage = profile.blockImages
            blockNetworkLoads = profile.offline
            cacheMode = when (profile.cacheMode) {
                CacheMode.DEFAULT -> WebSettings.LOAD_DEFAULT
                CacheMode.NO_CACHE -> WebSettings.LOAD_NO_CACHE
                CacheMode.CACHE_FIRST -> WebSettings.LOAD_CACHE_ELSE_NETWORK
                CacheMode.CACHE_ONLY -> WebSettings.LOAD_CACHE_ONLY
            }
            mixedContentMode = when (profile.mixedContent) {
                MixedContent.NEVER -> WebSettings.MIXED_CONTENT_NEVER_ALLOW
                MixedContent.COMPATIBLE -> WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                MixedContent.ALWAYS -> WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            setSupportMultipleWindows(profile.popups)
            javaScriptCanOpenWindowsAutomatically = profile.popups
            allowFileAccess = profile.fileAccess
            allowContentAccess = profile.contentAccess
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = profile.universalFileAccess
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = profile.universalFileAccess
            setGeolocationEnabled(profile.permissions != PermissionMode.DENY)
            safeBrowsingEnabled = profile.safeBrowsing
            offscreenPreRaster = profile.offscreenPreRaster
            userAgentString = userAgent(profile)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, profile.darkening)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(profile.cookies)
            setAcceptThirdPartyCookies(webView, profile.thirdPartyCookies)
        }
        WebView.setWebContentsDebuggingEnabled(profile.debugging)
        webView.setLayerType(if (profile.hardwareAcceleration) View.LAYER_TYPE_NONE else View.LAYER_TYPE_SOFTWARE, null)
        webView.isLongClickable = profile.longPress
        webView.setOnLongClickListener(if (profile.longPress) null else View.OnLongClickListener { true })
        if (previous?.userScript != profile.userScript) installUserScript(profile.userScript)
        if (previous == null || previous.proxy != profile.proxy || previous.proxyBypass != profile.proxyBypass) applyProxy(profile)
        if (profile.eruda && previous?.eruda == false) webView.evaluateJavascript(eruda, null)
        applied = profile
    }

    private fun userAgent(profile: Profile): String {
        val base = when (profile.userAgent) {
            UserAgentMode.DEFAULT -> defaultUserAgent
            UserAgentMode.DESKTOP -> desktopUserAgent(defaultUserAgent)
            UserAgentMode.CUSTOM -> profile.customUserAgent.ifBlank { defaultUserAgent }
        }
        return if (profile.userAgentSuffix.isBlank()) base else "$base ${profile.userAgentSuffix.trim()}"
    }

    private fun installUserScript(script: String) {
        scriptHandler?.remove()
        scriptHandler = null
        scriptSource = null
        if (script.isBlank()) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            scriptHandler = WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        } else {
            scriptSource = script
        }
    }

    private fun applyProxy(profile: Profile) {
        val executor = Executor { it.run() }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (profile.proxy.isBlank()) {
                ProxyController.getInstance().clearProxyOverride(executor) {}
            } else {
                val rule = if (profile.proxy.contains("://")) profile.proxy.trim() else "http://${profile.proxy.trim()}"
                val config = ProxyConfig.Builder()
                    .addProxyRule(rule)
                    .apply { profile.proxyBypass.split(',', ' ', '\n').filter { it.isNotBlank() }.forEach { addBypassRule(it.trim()) } }
                    .build()
                ProxyController.getInstance().setProxyOverride(config, executor) {}
            }
        } else if (profile.proxy.isNotBlank()) {
            host.notify("This WebView doesn't support a proxy override")
        }
    }

    override fun load(url: String) {
        if (!webView.isAttachedToWindow) {
            webView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    v.post { loadNow(url) }
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })
            return
        }
        loadNow(url)
    }

    private fun loadNow(url: String) {
        val headers = profile.headerMap()
        if (headers.isEmpty()) webView.loadUrl(url) else webView.loadUrl(url, headers)
    }

    override fun reload(bypassCache: Boolean) {
        if (bypassCache) webView.clearCache(true)
        val headers = profile.headerMap()
        val url = webView.url
        if (headers.isEmpty() || url == null) webView.reload() else webView.loadUrl(url, headers)
    }

    override fun back() = webView.goBack()
    override fun forward() = webView.goForward()
    override fun stop() = webView.stopLoading()

    override fun setFrozen(frozen: Boolean) {
        this.frozen = frozen
        if (frozen) {
            webView.onPause()
            webView.pauseTimers()
        } else {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun clear(scope: ClearScope, done: () -> Unit) {
        val cookies = CookieManager.getInstance()
        if (scope == ClearScope.CACHE || scope == ClearScope.ALL) webView.clearCache(true)
        if (scope == ClearScope.HISTORY || scope == ClearScope.ALL) webView.clearHistory()
        if (scope == ClearScope.STORAGE || scope == ClearScope.ALL) {
            WebStorage.getInstance().deleteAllData()
            GeolocationPermissions.getInstance().clearAll()
            WebViewDatabase.getInstance(webView.context).clearHttpAuthUsernamePassword()
            @Suppress("DEPRECATION")
            webView.clearFormData()
        }
        if (scope == ClearScope.COOKIES || scope == ClearScope.ALL) {
            cookies.removeAllCookies { cookies.flush(); done() }
        } else {
            done()
        }
        _page.update { it.copy(canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()) }
    }

    override fun evaluate(script: String, result: (String) -> Unit) {
        webView.evaluateJavascript(script) { result(it ?: "null") }
    }

    override fun clearConsole() {
        _console.value = emptyList()
    }

    override fun record(entry: ConsoleEntry) {
        _console.update { (it + entry).takeLast(CONSOLE_LIMIT) }
    }

    override fun resume() {
        if (!frozen) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun pause() {
        webView.onPause()
    }

    override fun destroy() {
        scriptHandler?.remove()
        if (profile.proxy.isNotBlank() && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride({ it.run() }) {}
        }
        webView.resumeTimers()
        webView.stopLoading()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun updateNavigation(view: WebView, url: String? = view.url) {
        _page.update {
            it.copy(
                url = url ?: it.url,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward(),
                secure = url?.startsWith("https://") == true && !it.certificateError,
            )
        }
    }

    private fun download(url: String, userAgent: String, disposition: String?, mimeType: String?) {
        val scheme = url.toUri().scheme
        if (scheme != "http" && scheme != "https") {
            host.notify("Can't download $scheme: links")
            return
        }
        val name = URLUtil.guessFileName(url, disposition, mimeType)
        val request = DownloadManager.Request(url.toUri())
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .apply { CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) } }
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        runCatching {
            ContextCompat.getSystemService(webView.context, DownloadManager::class.java)?.enqueue(request)
            host.notify("Downloading $name")
        }.onFailure { host.notify("Download failed: ${it.message}") }
    }

    private fun openExternally(uri: Uri): Boolean {
        if (!profile.externalApps) {
            host.notify("Blocked ${uri.scheme}: link (external apps are off)")
            return true
        }
        val intent = if (uri.scheme == "intent") {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()?.apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        } ?: return true
        if (!host.openExternal(intent)) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null) load(fallback) else host.notify("No app can open ${uri.scheme}: links")
        }
        return true
    }

    private fun grantMapped(resources: Array<String>, done: (Array<String>) -> Unit) {
        val android = resources.flatMap {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> listOf(Manifest.permission.CAMERA)
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS)
                else -> emptyList()
            }
        }
        if (android.isEmpty()) {
            done(resources)
        } else {
            host.requestAndroidPermissions(android) { ok -> done(if (ok) resources else emptyArray()) }
        }
    }

    private fun decide(origin: String, resources: List<String>, grant: () -> Unit, deny: () -> Unit) {
        when (profile.permissions) {
            PermissionMode.GRANT -> grant()
            PermissionMode.DENY -> deny()
            PermissionMode.ASK -> host.prompt(Prompt.Permission(host.nextPromptId(), origin, resources, grant, deny))
        }
    }

    @SuppressLint("MissingOnRenderProcessGone")
    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    if (request.isForMainFrame && !profile.allowsHost(uri.host)) {
                        host.notify("Blocked navigation to ${uri.host}")
                        true
                    } else {
                        false
                    }
                }
                "about", "data", "blob", "javascript", "file", "content" -> false
                else -> openExternally(uri)
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            _page.update { it.copy(loading = true, certificateError = false, favicon = favicon ?: it.favicon) }
            updateNavigation(view, url)
            scriptSource?.let { view.evaluateJavascript(it, null) }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            _page.update { it.copy(loading = false, progress = 100, title = view.title ?: it.title) }
            updateNavigation(view, url)
            if (profile.eruda) view.evaluateJavascript(eruda, null)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            updateNavigation(view, url)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            _page.update { it.copy(certificateError = true, secure = false) }
            when (profile.ssl) {
                SslMode.IGNORE -> handler.proceed()
                SslMode.STRICT -> {
                    handler.cancel()
                    host.notify("Certificate error on ${error.url.toUri().host}: ${sslProblem(error)}")
                }
                SslMode.ASK -> host.prompt(
                    Prompt.Certificate(host.nextPromptId(), error.url, sslProblem(error), handler::proceed, handler::cancel),
                )
            }
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, hostName: String, realm: String) {
            host.prompt(Prompt.HttpAuth(host.nextPromptId(), hostName, realm, handler::proceed, handler::cancel))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            host.rendererGone()
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            _page.update { it.copy(progress = newProgress, loading = newProgress < 100) }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            _page.update { it.copy(title = title.orEmpty()) }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            _page.update { it.copy(favicon = icon) }
        }

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (profile.console) {
                val level = when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> ConsoleLevel.ERROR
                    ConsoleMessage.MessageLevel.WARNING -> ConsoleLevel.WARN
                    ConsoleMessage.MessageLevel.DEBUG -> ConsoleLevel.DEBUG
                    ConsoleMessage.MessageLevel.TIP -> ConsoleLevel.INFO
                    else -> ConsoleLevel.LOG
                }
                val source = message.sourceId()?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }?.let { "$it:${message.lineNumber()}" }
                record(ConsoleEntry(level, message.message(), source.orEmpty()))
            }
            return false
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            host.enterFullscreen(view) { callback.onCustomViewHidden() }
        }

        override fun onHideCustomView() {
            host.exitFullscreen()
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val origin = request.origin.toString()
            decide(origin, request.resources.map { it.substringAfterLast('.') }, {
                grantMapped(request.resources) { granted -> if (granted.isEmpty()) request.deny() else request.grant(granted) }
            }, request::deny)
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            decide(origin, listOf("location"), {
                host.requestAndroidPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { ok ->
                    callback.invoke(origin, ok, false)
                }
            }, { callback.invoke(origin, false, false) })
        }

        override fun onShowFileChooser(view: WebView, filePathCallback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            val intent = runCatching { params.createIntent() }.getOrNull() ?: return false
            host.pickFiles(intent) { filePathCallback.onReceiveValue(it) }
            return true
        }

        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean = blockDialog(result)

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean = blockDialog(result)

        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean =
            blockDialog(result)

        override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean = blockDialog(result)

        private fun blockDialog(result: JsResult): Boolean {
            if (profile.jsDialogs) return false
            result.cancel()
            return true
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!profile.popups) return false
            val catcher = WebView(view.context)
            catcher.webViewClient = @SuppressLint("MissingOnRenderProcessGone") object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    load(request.url.toString())
                    v.destroy()
                    return true
                }

                override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                    v.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = catcher
            resultMsg.sendToTarget()
            return true
        }
    }
}

private fun sslProblem(error: SslError): String = when (error.primaryError) {
    SslError.SSL_EXPIRED -> "the certificate has expired"
    SslError.SSL_IDMISMATCH -> "the certificate is for another host"
    SslError.SSL_NOTYETVALID -> "the certificate is not valid yet"
    SslError.SSL_UNTRUSTED -> "the certificate authority is not trusted"
    SslError.SSL_DATE_INVALID -> "the certificate date is invalid"
    else -> "the certificate is invalid"
}
