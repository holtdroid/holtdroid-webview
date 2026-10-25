package io.github.holtdroid.webview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.holtdroid.webview.AppViewModel
import io.github.holtdroid.webview.engine.BrowserEngine
import io.github.holtdroid.webview.engine.Capability
import io.github.holtdroid.webview.engine.ConsoleEntry
import io.github.holtdroid.webview.engine.ConsoleLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleSheet(vm: AppViewModel, engine: BrowserEngine, onDismiss: () -> Unit) {
    val entries by engine.console.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val canEval = Capability.EVALUATE in engine.capabilities
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) list.animateScrollToItem(entries.lastIndex)
    }

    fun run() {
        val script = input.trim()
        if (script.isEmpty()) return
        engine.record(ConsoleEntry(ConsoleLevel.INPUT, script))
        vm.evaluate(script) { engine.record(ConsoleEntry(ConsoleLevel.RESULT, it)) }
        input = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxHeight(0.85f)
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Console", style = MaterialTheme.typography.titleLarge)
                    Text("${entries.size} messages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = engine::clearConsole) { Icon(Icons.Rounded.DeleteSweep, "Clear") }
            }
            LazyColumn(
                state = list,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            "Nothing logged yet. console.log() calls from the page show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(entries) { ConsoleLine(it) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = canEval,
                    placeholder = { Text(if (canEval) "Evaluate JavaScript" else "This engine can't evaluate scripts") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSend = { run() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = ::run, enabled = canEval && input.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Run") }
            }
        }
    }
}

@Composable
private fun ConsoleLine(entry: ConsoleEntry) {
    val scheme = MaterialTheme.colorScheme
    val (accent, background) = when (entry.level) {
        ConsoleLevel.ERROR -> scheme.error to scheme.errorContainer.copy(alpha = 0.35f)
        ConsoleLevel.WARN -> Color(0xFFE0A100) to Color(0xFFE0A100).copy(alpha = 0.12f)
        ConsoleLevel.INPUT -> scheme.primary to Color.Transparent
        ConsoleLevel.RESULT -> scheme.secondary to Color.Transparent
        else -> scheme.outline to Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            when (entry.level) {
                ConsoleLevel.INPUT -> "›"
                ConsoleLevel.RESULT -> "‹"
                ConsoleLevel.ERROR -> "✕"
                ConsoleLevel.WARN -> "!"
                else -> "·"
            },
            color = accent,
            fontFamily = Mono,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            Column {
                Text(entry.message, fontFamily = Mono, style = MaterialTheme.typography.bodySmall)
                if (entry.source.isNotEmpty()) {
                    Text(entry.source, fontFamily = Mono, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                }
            }
        }
    }
}
