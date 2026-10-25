package io.github.holtdroid.webview

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.holtdroid.webview.engine.Providers
import io.github.holtdroid.webview.model.Store
import io.github.holtdroid.webview.ui.AppRoot
import io.github.holtdroid.webview.ui.Keyboard
import io.github.holtdroid.webview.ui.KeyboardResult

class MainActivity : AppCompatActivity(), ActivityBridge {
    private val vm: AppViewModel by viewModels()

    private var permissionsDone: ((Boolean) -> Unit)? = null
    private val permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionsDone?.invoke(result.values.all { it })
        permissionsDone = null
    }

    private var filesDone: ((Array<Uri>?) -> Unit)? = null
    private val filesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        filesDone?.invoke(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        filesDone = null
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            vm.handle(intent.controlArgs(), fromStart = false) { reply ->
                log(reply)
                pending.resultCode = if (reply.ok) Activity.RESULT_OK else 1
                pending.resultData = reply.message
                pending.finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppViewModel.applyTheme(vm.state.value.theme)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.bind(this, this)
        ContextCompat.registerReceiver(this, receiver, IntentFilter(ACTION_CONTROL), ContextCompat.RECEIVER_EXPORTED)
        setContent { AppRoot(vm) }
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val args = intent.controlArgs()
        if (args.isEmpty()) return
        vm.handle(args, fromStart = true) { reply ->
            log(reply)
            if (!reply.ok) vm.notify(reply.message)
        }
    }

    private fun log(reply: Reply) {
        if (reply.ok) Log.i(Store.TAG, reply.message) else Log.w(Store.TAG, reply.message)
    }

    override fun onResume() {
        super.onResume()
        vm.session.value?.engine?.resume()
    }

    override fun onPause() {
        vm.session.value?.engine?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        vm.unbind(this)
        super.onDestroy()
    }

    override fun askPermissions(permissions: List<String>, done: (Boolean) -> Unit) {
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            done(true)
            return
        }
        permissionsDone?.invoke(false)
        permissionsDone = done
        permissionsLauncher.launch(missing.toTypedArray())
    }

    override fun pickFiles(intent: Intent, done: (Array<Uri>?) -> Unit) {
        filesDone?.invoke(null)
        filesDone = done
        runCatching { filesLauncher.launch(intent) }.onFailure {
            filesDone = null
            done(null)
        }
    }

    override fun startExternal(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    override fun openCustomTab(url: String) {
        runCatching { Providers.openCustomTab(this, url, getColor(R.color.brand)) }
            .onFailure { vm.notify("No browser supports Custom Tabs") }
    }

    override fun keyboard(show: Boolean?): KeyboardResult = Keyboard.toggle(this, vm.session.value?.engine?.view, show)

    companion object {
        const val ACTION_CONTROL = "io.github.holtdroid.webview.CONTROL"
        const val SCHEME = "holtdroid-webview"
    }
}

fun Intent.controlArgs(): Map<String, String> {
    val args = linkedMapOf<String, String>()
    val uri = data
    when (uri?.scheme?.lowercase()) {
        "http", "https" -> args["url"] = uri.toString()
        MainActivity.SCHEME -> {
            uri.host?.takeIf { it.isNotBlank() }?.let { args["cmd"] = it }
            uri.queryParameterNames.forEach { name -> uri.getQueryParameter(name)?.let { args[name] = it } }
        }
    }
    extras?.let { bundle ->
        bundle.keySet().filterNot { it.startsWith("android.") || it.startsWith("com.android.") }.forEach { key ->
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            if (value != null) args[key] = value.toString()
        }
    }
    return args
}
