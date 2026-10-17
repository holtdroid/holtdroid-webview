package io.github.kriakiku.holtdroid.webview.model

object DefaultPresets {
    val all = listOf(
        Preset(
            id = "browse",
            name = "Browse",
            description = "A regular browser window: toolbar, strict TLS, DevTools on.",
            icon = "web",
        ),
        Preset(
            id = "unlocked",
            name = "Unlocked",
            description = "Everything allowed for debugging: TLS errors ignored, mixed content, file access, popups, permissions granted, Eruda and console.",
            icon = "bug",
            profile = Profile(
                ssl = SslMode.IGNORE,
                mixedContent = MixedContent.ALWAYS,
                fileAccess = true,
                universalFileAccess = true,
                popups = true,
                permissions = PermissionMode.GRANT,
                safeBrowsing = false,
                eruda = true,
                mediaAutoplay = true,
                keepScreenOn = true,
            ),
        ),
        Preset(
            id = "kiosk",
            name = "Kiosk",
            description = "Full screen with no browser UI, navigation locked to the start host, no zoom, popups or long press.",
            icon = "kiosk",
            profile = Profile(
                display = DisplayMode.KIOSK,
                lockBack = true,
                zoom = false,
                longPress = false,
                externalApps = false,
                allowedHosts = "google.com",
                keepScreenOn = true,
            ),
        ),
        Preset(
            id = "app",
            name = "App shell",
            description = "How an app's WebView usually looks: edge to edge, no browser UI, a floating handle for controls.",
            icon = "phone",
            profile = Profile(display = DisplayMode.EDGE, zoom = false),
        ),
        Preset(
            id = "small",
            name = "Small phone",
            description = "A 360 × 640 viewport framed in the middle of the screen, for responsive layouts.",
            icon = "resize",
            profile = Profile(width = 360, height = 640),
        ),
        Preset(
            id = "desktop",
            name = "Desktop site",
            description = "Desktop user agent with an overview viewport, like Chrome's \"Desktop site\".",
            icon = "desktop",
            profile = Profile(userAgent = UserAgentMode.DESKTOP, overviewMode = true, orientation = Orientation.LANDSCAPE),
        ),
        Preset(
            id = "fresh",
            name = "Fresh start",
            description = "Wipes cookies, storage and cache on every launch and skips the HTTP cache.",
            icon = "fresh",
            profile = Profile(clearOnStart = true, cacheMode = CacheMode.NO_CACHE),
        ),
        Preset(
            id = "proxy",
            name = "Proxy",
            description = "Traffic through a local proxy (mitmproxy, Charles) on 127.0.0.1:8080 with TLS errors ignored. Forward the port with ssh -R or adb reverse.",
            icon = "proxy",
            profile = Profile(proxy = "127.0.0.1:8080", ssl = SslMode.IGNORE),
        ),
    )
}
