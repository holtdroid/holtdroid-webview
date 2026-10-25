package io.github.holtdroid.webview.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

enum class KeyboardResult { SHOWN, HIDDEN, BLOCKED }

object Keyboard {
    private const val SETTING = "show_ime_with_hard_keyboard"

    const val ALLOW_COMMAND = "adb shell settings put secure $SETTING 1"

    fun grantCommand(context: Context) = "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"

    fun visible(activity: Activity): Boolean =
        ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun blockedByHardware(context: Context): Boolean {
        val config = context.resources.configuration
        val hard = config.keyboard != Configuration.KEYBOARD_NOKEYS && config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        return hard && Settings.Secure.getInt(context.contentResolver, SETTING, 0) == 0
    }

    private fun allowWithHardware(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching { Settings.Secure.putInt(context.contentResolver, SETTING, 1) }.getOrDefault(false)
    }

    fun toggle(activity: Activity, target: View?, show: Boolean? = null): KeyboardResult {
        val controller = WindowCompat.getInsetsController(activity.window, target ?: activity.window.decorView)
        if (!(show ?: !visible(activity))) {
            controller.hide(WindowInsetsCompat.Type.ime())
            return KeyboardResult.HIDDEN
        }
        if (blockedByHardware(activity) && !allowWithHardware(activity)) return KeyboardResult.BLOCKED
        if (target != null && !target.hasFocus()) target.requestFocus()
        controller.show(WindowInsetsCompat.Type.ime())
        return KeyboardResult.SHOWN
    }
}
