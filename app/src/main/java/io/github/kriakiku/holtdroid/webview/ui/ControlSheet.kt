package io.github.kriakiku.holtdroid.webview.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kriakiku.holtdroid.webview.AppViewModel
import io.github.kriakiku.holtdroid.webview.Session
import io.github.kriakiku.holtdroid.webview.engine.Capability
import io.github.kriakiku.holtdroid.webview.engine.ClearScope
import io.github.kriakiku.holtdroid.webview.engine.PageState
import io.github.kriakiku.holtdroid.webview.engine.Providers
import io.github.kriakiku.holtdroid.webview.model.AppTheme
import io.github.kriakiku.holtdroid.webview.model.DisplayMode
import io.github.kriakiku.holtdroid.webview.model.EngineKind
import io.github.kriakiku.holtdroid.webview.model.ToolbarPosition
import io.github.kriakiku.holtdroid.webview.model.UserAgentMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlSheet(vm: AppViewModel, session: Session, page: PageState, onDismiss: () -> Unit, onConsole: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val viewport by vm.viewport.collectAsStateWithLifecycle()
    val profile = session.profile
    val engine = session.engine
    val clipboard = rememberClipboard()
    val context = LocalContext.current
    val provider = remember(engine.kind) { Providers.current(context) }
    val dark = state.theme == AppTheme.DARK

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    page.favicon?.let {
                        Image(it.asImageBitmap(), null, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(page.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(page.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${session.preset.name} · ${engine.kind.label()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { clipboard(page.url) }) { Icon(Icons.Rounded.ContentCopy, "Copy address") }
                    IconButton(onClick = { onDismiss(); vm.goHome() }) { Icon(Icons.Rounded.Home, "Close the page") }
                }
            }
            item {
                val tiles = listOf(
                    TileSpec("Freeze", Icons.Rounded.AcUnit, session.frozen) { vm.freeze(!session.frozen) },
                    TileSpec("Hard reload", Icons.Rounded.Refresh) { engine.reload(bypassCache = true); onDismiss() },
                    TileSpec("Desktop", Icons.Rounded.DesktopWindows, profile.userAgent == UserAgentMode.DESKTOP) {
                        val desktop = profile.userAgent != UserAgentMode.DESKTOP
                        vm.update { it.copy(userAgent = if (desktop) UserAgentMode.DESKTOP else UserAgentMode.DEFAULT, overviewMode = desktop) }
                        engine.reload()
                    },
                    TileSpec("Dark", Icons.Rounded.DarkMode, dark) { vm.setTheme(if (dark) AppTheme.LIGHT else AppTheme.DARK) },
                    TileSpec("Console", Icons.Rounded.Terminal, enabled = Capability.CONSOLE in engine.capabilities) { onConsole() },
                    TileSpec("Eruda", Icons.Rounded.BugReport, profile.eruda, enabled = Capability.ERUDA in engine.capabilities) {
                        vm.update { it.copy(eruda = !it.eruda) }
                    },
                    TileSpec("Rotate", Icons.Rounded.ScreenRotation, enabled = profile.framed) {
                        vm.update { it.copy(width = it.height, height = it.width) }
                    },
                    TileSpec("Settings", Icons.Rounded.Tune) { onDismiss(); vm.editLive() },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { Tile(it, Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                Group("Display") {
                    val modes = DisplayMode.entries
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = profile.display == mode,
                                onClick = { vm.update { it.copy(display = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(mode.label(), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Toolbar", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = profile.toolbar == ToolbarPosition.TOP,
                            onClick = { vm.update { it.copy(toolbar = ToolbarPosition.TOP) } },
                            label = { Text("Top") },
                            leadingIcon = { Icon(Icons.Rounded.VerticalAlignTop, null, Modifier.size(18.dp)) },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = profile.toolbar == ToolbarPosition.BOTTOM,
                            onClick = { vm.update { it.copy(toolbar = ToolbarPosition.BOTTOM) } },
                            label = { Text("Bottom") },
                            leadingIcon = { Icon(Icons.Rounded.VerticalAlignBottom, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            item {
                Group("Viewport", trailing = viewport?.let { "${it.width} × ${it.height} · ${it.scale}%" }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Frames) { frame ->
                            FilterChip(
                                selected = profile.width == frame.width && profile.height == frame.height,
                                onClick = { vm.update { it.copy(width = frame.width, height = frame.height) } },
                                label = { Text(frame.label) },
                            )
                        }
                    }
                    if (profile.framed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (profile.scale == 0) "Scale: fit" else "Scale: ${profile.scale}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                            Slider(
                                value = profile.scale.toFloat(),
                                onValueChange = { v -> vm.update { it.copy(scale = (v / 5).toInt() * 5) } },
                                valueRange = 0f..200f,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                Group("Clear data") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Cache", Icons.Rounded.Layers, ClearScope.CACHE),
                            Triple("Cookies", Icons.Rounded.Cookie, ClearScope.COOKIES),
                            Triple("Storage", Icons.Rounded.Storage, ClearScope.STORAGE),
                            Triple("All", Icons.Rounded.DeleteSweep, ClearScope.ALL),
                        ).forEach { (label, icon, scope) ->
                            Tile(TileSpec(label, icon) { vm.clear(scope) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Group("DevTools") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Remote debugging", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (engine.kind == EngineKind.GECKO) {
                                    "Firefox → about:debugging → this device"
                                } else {
                                    "chrome://inspect on a computer with adb, or Holtdroid's DevTools tab"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = profile.debugging, onCheckedChange = { on -> vm.update { it.copy(debugging = on) } })
                    }
                    if (profile.debugging) {
                        val forward = "adb forward tcp:9222 localabstract:${engine.debugTarget}"
                        Surface(
                            onClick = { clipboard(forward) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                Text(forward, fontFamily = Mono, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.ContentCopy, "Copy", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            item {
                Group("Engine", trailing = if (engine.kind == EngineKind.WEBVIEW) provider?.let { "${it.label} ${it.version}" } else null) {
                    EngineChooser(profile.engine) { kind ->
                        if (kind == EngineKind.CUSTOM_TABS) onDismiss()
                        vm.update { it.copy(engine = kind) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class TileSpec(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun Tile(spec: TileSpec, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = spec.onClick,
        enabled = spec.enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (spec.active) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (spec.active) scheme.onPrimaryContainer else scheme.onSurface.copy(alpha = if (spec.enabled) 1f else 0.38f),
        modifier = modifier.aspectRatio(1.1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(6.dp)) {
            Icon(spec.icon, null)
            Spacer(Modifier.height(6.dp))
            Text(spec.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Group(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.outline) }
        }
        content()
    }
}

fun EngineKind.label(): String = when (this) {
    EngineKind.WEBVIEW -> "WebView"
    EngineKind.GECKO -> "GeckoView"
    EngineKind.CUSTOM_TABS -> "Custom Tab"
}

fun DisplayMode.label(): String = when (this) {
    DisplayMode.STANDARD -> "Standard"
    DisplayMode.IMMERSIVE -> "Immersive"
    DisplayMode.EDGE -> "Edge"
    DisplayMode.KIOSK -> "Kiosk"
}
