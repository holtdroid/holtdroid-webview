package io.github.kriakiku.holtdroid.webview.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kriakiku.holtdroid.webview.AppViewModel
import io.github.kriakiku.holtdroid.webview.Screen
import io.github.kriakiku.holtdroid.webview.engine.GeckoSupport
import io.github.kriakiku.holtdroid.webview.model.EngineKind
import io.github.kriakiku.holtdroid.webview.model.FieldType
import io.github.kriakiku.holtdroid.webview.model.Profile
import io.github.kriakiku.holtdroid.webview.model.ProfileFields
import io.github.kriakiku.holtdroid.webview.model.override
import io.github.kriakiku.holtdroid.webview.model.value

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, screen: Screen.Editor) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    BackHandler { vm.closeEditor() }

    fun setProfile(change: (Profile) -> Profile) {
        vm.draft.value = draft.copy(profile = change(draft.profile))
    }

    fun set(key: String, value: String) = setProfile { it.override(mapOf(key to value)).profile }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = vm::closeEditor) { Icon(Icons.Rounded.Close, "Close") } },
                title = {
                    Text(
                        when {
                            screen.live -> "Page settings"
                            screen.presetId == null -> "New preset"
                            else -> "Edit preset"
                        },
                    )
                },
                actions = {
                    if (screen.live) {
                        TextButton(onClick = {
                            val saved = vm.savePreset(draft)
                            vm.draft.value = saved
                            vm.notify("Saved as ${saved.name}")
                        }) { Text("Save preset") }
                        TextButton(onClick = vm::applyDraftLive) { Text("Apply") }
                    } else {
                        TextButton(onClick = {
                            vm.savePreset(draft)
                            vm.closeEditor()
                        }) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            item {
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            draft.name, { vm.draft.value = draft.copy(name = it) },
                            label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            draft.description, { vm.draft.value = draft.copy(description = it) },
                            label = { Text("Description") }, modifier = Modifier.fillMaxWidth(),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(PresetIcons.keys.toList()) { key ->
                                FilledIconToggleButton(checked = draft.icon == key, onCheckedChange = { vm.draft.value = draft.copy(icon = key) }) {
                                    Icon(presetIcon(key), key)
                                }
                            }
                        }
                        OutlinedTextField(
                            draft.profile.url, { v -> setProfile { it.copy(url = v) } },
                            label = { Text("Start address") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            supportingText = { KeyLabel("url") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Engine", style = MaterialTheme.typography.labelLarge)
                        EngineChooser(draft.profile.engine) { e -> setProfile { it.copy(engine = e) } }
                        KeyLabel("engine")
                    }
                }
            }
            item {
                OutlinedTextField(
                    filter, { filter = it },
                    placeholder = { Text("Find a setting") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Section.entries.forEach { section ->
                val defs = Catalog.filter { it.section == section && it.matches(filter) }
                if (defs.isEmpty()) return@forEach
                item(key = section.name) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) {
                        Icon(section.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(defs, key = { it.key }) { def ->
                    SectionCard {
                        SettingRow(def, draft.profile, onSet = { k, v -> set(k, v) }, onProfile = ::setProfile)
                    }
                }
            }
        }
    }
}

private fun SettingDef.matches(filter: String): Boolean {
    if (filter.isBlank()) return true
    val q = filter.trim().lowercase()
    return title.lowercase().contains(q) || description.lowercase().contains(q) || key.lowercase().contains(q)
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
fun KeyLabel(key: String) {
    Text(key, style = MaterialTheme.typography.labelSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.outline)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineChooser(selected: EngineKind, onSelect: (EngineKind) -> Unit) {
    val options = listOf(EngineKind.WEBVIEW to "WebView", EngineKind.GECKO to "GeckoView", EngineKind.CUSTOM_TABS to "Custom Tab")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (kind, label) ->
            SegmentedButton(
                selected = selected == kind,
                onClick = { onSelect(kind) },
                enabled = kind != EngineKind.GECKO || GeckoSupport.AVAILABLE,
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingRow(def: SettingDef, profile: Profile, onSet: (String, String) -> Unit, onProfile: ((Profile) -> Profile) -> Unit) {
    val field = ProfileFields.find(def.key) ?: return
    val supported = profile.engine in def.engines || profile.engine == EngineKind.CUSTOM_TABS
    val value = profile.value(def.key).orEmpty()
    val header: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Text(def.title, style = MaterialTheme.typography.titleSmall)
            Text(def.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                KeyLabel(def.key)
                if (!supported) {
                    Spacer(Modifier.width(8.dp))
                    Text("WebView only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .alpha(if (supported) 1f else 0.55f)
    when {
        def.key == "width" -> Column(rowModifier.padding(16.dp)) {
            header(Modifier)
            Spacer(Modifier.height(10.dp))
            ViewportEditor(profile, onProfile)
        }
        field.type == FieldType.BOOLEAN -> Row(
            rowModifier
                .clickable { onSet(def.key, (value != "true").toString()) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            header(Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = value == "true", onCheckedChange = { onSet(def.key, it.toString()) })
        }
        field.type == FieldType.ENUM -> Column(rowModifier.padding(16.dp)) {
            header(Modifier)
            Spacer(Modifier.height(10.dp))
            val options = field.options
            if (options.size <= 4) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = value == option,
                            onClick = { onSet(def.key, option) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(def.labels[option] ?: option, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        FilterChip(selected = value == option, onClick = { onSet(def.key, option) }, label = { Text(def.labels[option] ?: option) })
                    }
                }
            }
        }
        field.type == FieldType.INT -> Column(rowModifier.padding(16.dp)) {
            val range = def.range ?: 0..100
            val number = value.toIntOrNull() ?: range.first
            Row(verticalAlignment = Alignment.CenterVertically) {
                header(Modifier.weight(1f))
                Text(
                    if (def.key == "scale" && number == 0) "Fit" else if (def.key == "scale" || def.key == "textZoom") "$number%" else "$number",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = number.toFloat(),
                onValueChange = { onSet(def.key, ((it / def.step).toInt() * def.step).toString()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
            )
        }
        else -> Column(rowModifier.padding(16.dp)) {
            header(Modifier)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { onSet(def.key, it) },
                placeholder = { Text(def.placeholder) },
                singleLine = !def.multiline,
                minLines = if (def.multiline) 3 else 1,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViewportEditor(profile: Profile, onProfile: ((Profile) -> Profile) -> Unit) {
    var width by remember(profile.width) { mutableStateOf(if (profile.width > 0) profile.width.toString() else "") }
    var height by remember(profile.height) { mutableStateOf(if (profile.height > 0) profile.height.toString() else "") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Frames.forEach { frame ->
            FilterChip(
                selected = profile.width == frame.width && profile.height == frame.height,
                onClick = { onProfile { it.copy(width = frame.width, height = frame.height) } },
                label = { Text(frame.label) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            width,
            { v ->
                width = v.filter(Char::isDigit).take(4)
                onProfile { it.copy(width = width.toIntOrNull() ?: 0) }
            },
            label = { Text("Width") }, placeholder = { Text("fill") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Text("×")
        OutlinedTextField(
            height,
            { v ->
                height = v.filter(Char::isDigit).take(4)
                onProfile { it.copy(height = height.toIntOrNull() ?: 0) }
            },
            label = { Text("Height") }, placeholder = { Text("fill") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onProfile { it.copy(width = it.height, height = it.width) } }) {
            Icon(Icons.Rounded.ScreenRotation, "Rotate")
        }
    }
}
