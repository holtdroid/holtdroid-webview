package io.github.holtdroid.webview.engine

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat

data class WebViewProvider(val packageName: String, val label: String, val version: String, val current: Boolean)

object Providers {
    private val known = listOf(
        "com.google.android.webview",
        "com.google.android.webview.beta",
        "com.google.android.webview.dev",
        "com.google.android.webview.canary",
        "com.android.webview",
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.bromite.webview",
        "org.cromite.webview",
        "app.vanadium.webview",
        "com.huawei.webview",
    )

    fun current(context: Context): WebViewProvider? {
        val info = WebViewCompat.getCurrentWebViewPackage(context) ?: return null
        return WebViewProvider(
            info.packageName,
            label(context, info.packageName),
            info.versionName.orEmpty(),
            current = true,
        )
    }

    fun installed(context: Context): List<WebViewProvider> {
        val currentPackage = WebViewCompat.getCurrentWebViewPackage(context)?.packageName
        val pm = context.packageManager
        return known.mapNotNull { name ->
            val info = runCatching { pm.getPackageInfo(name, 0) }.getOrNull() ?: return@mapNotNull null
            if (info.applicationInfo?.enabled == false) return@mapNotNull null
            WebViewProvider(name, label(context, name), info.versionName.orEmpty(), name == currentPackage)
        }
    }

    private fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
    }.getOrDefault(packageName)

    fun switchCommand(packageName: String) = "adb shell cmd webviewupdate set-webview-implementation $packageName"

    fun customTabsPackage(context: Context): String? = CustomTabsClient.getPackageName(context, null)

    fun openCustomTab(activity: Activity, url: String, toolbarColor: Int) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
            .build()
            .launchUrl(activity, url.toUri())
    }
}
