package io.github.holtdroid.webview.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.holtdroid.webview.AppViewModel
import io.github.holtdroid.webview.BuildConfig
import io.github.holtdroid.webview.R
import io.github.holtdroid.webview.engine.GeckoSupport
import io.github.holtdroid.webview.engine.Providers
import io.github.holtdroid.webview.engine.WebViewProvider
import io.github.holtdroid.webview.model.AppTheme
import io.github.holtdroid.webview.model.Preset
import io.github.holtdroid.webview.model.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    var launchWith by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var engines by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Preset?>(null) }
    val selected = state.preset(launchWith) ?: state.default
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    fun launch(preset: Preset, target: String? = url.takeIf { it.isNotBlank() }) {
        vm.open(preset, url = target)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Holtdroid WebView", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "DevTools unlocked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.setTheme(AppTheme.entries[(state.theme.ordinal + 1) % AppTheme.entries.size])
                    }) {
                        Icon(
                            when (state.theme) {
                                AppTheme.SYSTEM -> Icons.Rounded.BrightnessAuto
                                AppTheme.LIGHT -> Icons.Rounded.LightMode
                                AppTheme.DARK -> Icons.Rounded.Brightness4
                            },
                            "Theme: ${state.theme.name.lowercase()}",
                        )
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Settings, "App settings") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                LaunchCard(
                    url = url,
                    onUrl = { url = it },
                    preset = selected,
                    presets = state.presets,
                    onPreset = { launchWith = it.id },
                    onGo = { launch(selected) },
                )
            }
            if (state.recents.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.recents) { recent ->
                            SuggestionChip(
                                onClick = { launch(selected, recent) },
                                label = { Text(recent.removePrefix("https://").removeSuffix("/"), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
                                icon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
            item { EngineCard(onClick = { engines = true }) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Presets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.edit(null) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New preset")
                    }
                }
            }
            items(state.presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    isDefault = preset.id == state.default.id,
                    onOpen = { launch(preset, url.takeIf { it.isNotBlank() } ?: preset.profile.url) },
                    onEdit = { vm.edit(preset) },
                    onDuplicate = { vm.duplicate(preset) },
                    onDefault = { vm.setDefault(preset) },
                    onMove = { vm.move(preset, it) },
                    onDelete = { deleting = preset },
                )
            }
            item { AdbCard() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = { vm.open(Preset("docs", "Docs"), url = DOCS_URL) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Documentation")
                    }
                    FilledTonalButton(onClick = { vm.open(Preset("docs", "Releases"), url = RELEASES_URL) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Releases")
                    }
                }
            }
            item {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} · made for Holtdroid",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (menu) AppSettingsSheet(vm, onDismiss = { menu = false })
    if (engines) EnginesDialog(onDismiss = { engines = false })
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${preset.name}?") },
            text = { Text("The preset is removed from this device. Built-in presets come back with Restore built-in presets.") },
            confirmButton = { TextButton(onClick = { vm.delete(preset); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LaunchCard(
    url: String,
    onUrl: (String) -> Unit,
    preset: Preset,
    presets: List<Preset>,
    onPreset: (Preset) -> Unit,
    onGo: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var choosing by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.secondaryContainer)))
                .padding(20.dp),
        ) {
            Text("Open a page", style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = onUrl,
                placeholder = { Text("Address or search") },
                leadingIcon = { Icon(Icons.Rounded.Public, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Surface(
                        onClick = { choosing = true },
                        shape = CircleShape,
                        color = scheme.surface.copy(alpha = 0.7f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(presetIcon(preset.icon), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                        }
                    }
                    DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                        presets.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                leadingIcon = { Icon(presetIcon(p.icon), null) },
                                onClick = { onPreset(p); choosing = false },
                            )
                        }
                    }
                }
                Button(onClick = onGo, enabled = url.isNotBlank()) {
                    Text("Open")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun IconBadge(key: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (bg, fg) = palette[(PresetIcons.keys.indexOf(key).coerceAtLeast(0)) % palette.size]
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(presetIcon(key), null, tint = fg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(profile: Profile) {
    val tags = profile.tags()
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            val scheme = MaterialTheme.colorScheme
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (tag.warning) scheme.errorContainer else scheme.surfaceContainerHighest,
                contentColor = if (tag.warning) scheme.onErrorContainer else scheme.onSurfaceVariant,
            ) {
                Text(tag.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    isDefault: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDefault: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)) {
            IconBadge(preset.icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Star, "Default", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (preset.description.isNotBlank()) {
                    Text(
                        preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    preset.profile.url.removePrefix("https://"),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = Mono,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                TagRow(preset.profile)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(
                        text = { Text("Make default") },
                        leadingIcon = { Icon(if (isDefault) Icons.Rounded.Star else Icons.Rounded.StarOutline, null) },
                        enabled = !isDefault,
                        onClick = { menu = false; onDefault() },
                    )
                    DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) }, onClick = { menu = false; onMove(1) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun EngineCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val provider = remember { Providers.current(context) }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Rounded.Web, null) },
            overlineContent = { Text("Engines") },
            headlineContent = { Text(provider?.let { "${it.label} ${it.version}" } ?: "No WebView provider") },
            supportingContent = {
                Text(
                    if (GeckoSupport.AVAILABLE) "GeckoView ${GeckoSupport.VERSION} is built in · tap to switch the WebView provider" else "Tap to switch the WebView provider or get GeckoView",
                )
            },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) },
        )
    }
}

@Composable
private fun EnginesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val clipboard = rememberClipboard()
    val providers = remember { Providers.installed(context) }
    val tabs = remember { Providers.customTabsPackage(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Engines") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "Android picks one WebView provider for every app. Only the system (or adb) can change it; copy a command and run it on a computer connected to the device.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(providers) { p -> ProviderRow(p) { clipboard(Providers.switchCommand(p.packageName)) } }
                item {
                    TextButton(onClick = {
                        runCatching { activity?.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                    }) { Text("Open developer options") }
                }
                item { HorizontalDivider() }
                item {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("GeckoView") },
                        supportingContent = {
                            Text(
                                if (GeckoSupport.AVAILABLE) {
                                    "Firefox's engine ${GeckoSupport.VERSION}, built into this APK. Pick it per preset."
                                } else {
                                    "Not in this build. Install the -gecko APK from the releases page to get Firefox's engine as a second option."
                                },
                            )
                        },
                    )
                }
                item {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Custom Tabs") },
                        supportingContent = { Text(tabs?.let { "Opens the page in $it, sharing the browser's cookies." } ?: "No browser with Custom Tabs support is installed.") },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ProviderRow(provider: WebViewProvider, onCopy: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (provider.current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(provider.label + if (provider.current) " · active" else "", style = MaterialTheme.typography.labelLarge)
                Text("${provider.packageName} ${provider.version}", style = MaterialTheme.typography.labelSmall, fontFamily = Mono)
            }
            if (!provider.current) IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy adb command") }
        }
    }
}

@Composable
private fun AdbCard() {
    val clipboard = rememberClipboard()
    val command = "adb shell am broadcast -a ${io.github.holtdroid.webview.MainActivity.ACTION_CONTROL} --es cmd resize --es size 360x640"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, null)
                Spacer(Modifier.width(12.dp))
                Text("Drive it from adb", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard(command) }) { Icon(Icons.Rounded.ContentCopy, "Copy") }
            }
            Text(
                "Every setting and action has a key: open presets, resize, freeze, clear data or evaluate JavaScript while the page is open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(command, fontFamily = Mono, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var wipe by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("App settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SwitchRow("Dynamic color", "Use the wallpaper colors (Android 12+).", state.dynamicColor, vm::setDynamicColor)
            SwitchRow("External control", "Accept settings and commands from adb and other apps.", state.externalControl, vm::setExternalControl)
            ListItem(
                headlineContent = { Text("Restore built-in presets") },
                supportingContent = { Text("Reset the presets that ship with the app; your own stay.") },
                modifier = Modifier.clickable { vm.restoreDefaults(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("Forget recent addresses") },
                modifier = Modifier.clickable { vm.forgetRecents(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("Clear all browsing data", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Cookies, storage, cache and history of every engine.") },
                modifier = Modifier.clickable { wipe = true },
            )
        }
    }
    if (wipe) {
        AlertDialog(
            onDismissRequest = { wipe = false },
            title = { Text("Clear all browsing data?") },
            text = { Text("Signs you out of every site and removes everything pages stored on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clear(io.github.holtdroid.webview.engine.ClearScope.ALL) { vm.notify("All browsing data cleared") }
                    wipe = false
                    onDismiss()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { wipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
