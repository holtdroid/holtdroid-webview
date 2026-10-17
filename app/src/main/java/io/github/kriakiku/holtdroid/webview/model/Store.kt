package io.github.kriakiku.holtdroid.webview.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class Store(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "state.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun load(): AppState = runCatching {
        if (file.exists()) AppJson.decodeFromString(AppState.serializer(), file.readText()) else AppState()
    }.getOrElse {
        Log.w(TAG, "state.json is unreadable, starting over", it)
        AppState()
    }

    fun update(change: (AppState) -> AppState) {
        _state.update(change)
        val snapshot = _state.value
        scope.launch(Dispatchers.IO) {
            synchronized(file) {
                val tmp = File(file.parentFile, "state.json.tmp")
                tmp.writeText(AppJson.encodeToString(AppState.serializer(), snapshot))
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        const val TAG = "HoltdroidWebView"
    }
}
