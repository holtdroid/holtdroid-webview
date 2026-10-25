package io.github.holtdroid.webview.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.holtdroid.webview.AppViewModel
import io.github.holtdroid.webview.Screen
import io.github.holtdroid.webview.engine.Prompt

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    HoltdroidTheme(dynamicColor = state.dynamicColor) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(targetState = screen, label = "screen") { current ->
                when (current) {
                    Screen.Home -> HomeScreen(vm)
                    is Screen.Editor -> EditorScreen(vm, current)
                    Screen.Browser -> BrowserScreen(vm)
                }
            }
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 72.dp),
            )
            Prompts(vm)
        }
    }
}

@Composable
private fun Prompts(vm: AppViewModel) {
    val prompts by vm.prompts.collectAsStateWithLifecycle()
    val prompt = prompts.firstOrNull() ?: return
    fun done(action: () -> Unit) {
        vm.resolve(prompt)
        action()
    }
    when (prompt) {
        is Prompt.Certificate -> AlertDialog(
            onDismissRequest = { done(prompt.cancel) },
            icon = { Icon(Icons.Rounded.GppMaybe, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Certificate problem") },
            text = { Text("${prompt.url}\n\n${prompt.problem.replaceFirstChar { it.uppercase() }}. Continue only if you trust this server, for example a local proxy or a staging host.") },
            confirmButton = { TextButton(onClick = { done(prompt.proceed) }) { Text("Proceed anyway", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { done(prompt.cancel) }) { Text("Go back") } },
        )
        is Prompt.Permission -> AlertDialog(
            onDismissRequest = { done(prompt.deny) },
            icon = { Icon(Icons.Rounded.Shield, null) },
            title = { Text("Allow ${prompt.resources.joinToString(", ").lowercase()}?") },
            text = { Text("${prompt.origin} asks for ${prompt.resources.joinToString(" and ").lowercase()}.") },
            confirmButton = { TextButton(onClick = { done(prompt.grant) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { done(prompt.deny) }) { Text("Deny") } },
        )
        is Prompt.HttpAuth -> {
            var user by remember(prompt.id) { mutableStateOf("") }
            var password by remember(prompt.id) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { done(prompt.cancel) },
                icon = { Icon(Icons.Rounded.Key, null) },
                title = { Text("Sign in to ${prompt.host}") },
                text = {
                    Column {
                        if (prompt.realm.isNotBlank()) Text(prompt.realm, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(user, { user = it }, label = { Text("User") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            password, { password = it }, label = { Text("Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { done { prompt.proceed(user, password) } }) { Text("Sign in") } },
                dismissButton = { TextButton(onClick = { done(prompt.cancel) }) { Text("Cancel") } },
            )
        }
    }
}
