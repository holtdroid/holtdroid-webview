package io.github.holtdroid.webview.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.holtdroid.webview.AppViewModel
import io.github.holtdroid.webview.R
import io.github.holtdroid.webview.Session
import io.github.holtdroid.webview.Viewport
import io.github.holtdroid.webview.engine.PageState
import io.github.holtdroid.webview.model.DisplayMode
import io.github.holtdroid.webview.model.Orientation
import io.github.holtdroid.webview.model.Profile
import io.github.holtdroid.webview.model.ToolbarPosition
import kotlin.math.roundToInt

@Composable
fun BrowserScreen(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val current = session ?: return
    val profile = current.profile
    val page by current.engine.page.collectAsStateWithLifecycle()
    val fullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    val viewport by vm.viewport.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(false) }
    var console by rememberSaveable { mutableStateOf(false) }

    WindowEffects(profile, fullscreen != null)

    val activity = LocalActivity.current
    var keyboardHelp by remember { mutableStateOf(false) }
    fun keyboard() {
        val target = current.engine.view
        if (activity != null && Keyboard.toggle(activity, target) == KeyboardResult.BLOCKED) keyboardHelp = true
    }

    BackHandler {
        when {
            fullscreen != null -> vm.leaveFullscreen()
            profile.lockBack -> sheet = true
            page.canGoBack -> current.engine.back()
            else -> vm.goHome()
        }
    }

    val toolbar: @Composable (Modifier) -> Unit = { modifier ->
        BrowserToolbar(
            page = page,
            onBack = current.engine::back,
            onForward = current.engine::forward,
            onReload = { if (page.loading) current.engine.stop() else current.engine.reload() },
            onSubmit = vm::load,
            onMenu = { sheet = true },
            onKeyboard = ::keyboard,
            modifier = modifier,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxSize()) {
            val standard = profile.display == DisplayMode.STANDARD
            val top = profile.toolbar == ToolbarPosition.TOP
            val barInsets = when {
                !standard -> Modifier.displayCutoutPadding()
                top -> Modifier.statusBarsPadding()
                else -> Modifier.navigationBarsPadding()
            }
            if (profile.showsToolbar && top) toolbar(barInsets)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        when {
                            !standard -> Modifier
                            !profile.showsToolbar -> Modifier
                            top -> Modifier.navigationBarsPadding()
                            else -> Modifier.statusBarsPadding()
                        },
                    )
                    .imePadding(),
            ) {
                PageViewport(vm, current)
                if (!profile.showsToolbar && page.loading) {
                    LinearProgressIndicator(
                        progress = { page.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.TopCenter),
                    )
                }
                if (profile.framed) SizeBadge(viewport, Modifier.align(Alignment.BottomCenter))
                androidx.compose.animation.AnimatedVisibility(current.frozen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                    FrozenOverlay { vm.freeze(false) }
                }
                if (!profile.showsToolbar && profile.handle) FloatingHandle(onClick = { sheet = true })
            }
            if (profile.showsToolbar && !top) toolbar(barInsets)
        }
        fullscreen?.let { fs ->
            AndroidView(
                factory = { fs.view },
                onRelease = { (it.parent as? android.view.ViewGroup)?.removeView(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }

    if (sheet) {
        ControlSheet(
            vm, current, page,
            onDismiss = { sheet = false },
            onConsole = { sheet = false; console = true },
            onKeyboard = { sheet = false; keyboard() },
        )
    }
    if (keyboardHelp) KeyboardHelp(onDismiss = { keyboardHelp = false })
    if (console) ConsoleSheet(vm, current.engine, onDismiss = { console = false })
}

@Composable
private fun PageViewport(vm: AppViewModel, session: Session) {
    val profile = session.profile
    val dots = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (profile.framed) {
                    Modifier.drawBehind {
                        val step = 18.dp.toPx()
                        var y = step / 2
                        while (y < size.height) {
                            var x = step / 2
                            while (x < size.width) {
                                drawCircle(dots, radius = 1.2.dp.toPx(), center = Offset(x, y))
                                x += step
                            }
                            y += step
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        AndroidView(
            factory = { context -> ViewportLayout(context) },
            update = { layout ->
                layout.onMetrics = { w, h, s -> vm.viewport.value = Viewport(w, h, s) }
                layout.show(session.engine.view)
                layout.configure(profile.width, profile.height, profile.scale)
            },
            onRelease = { it.removeAllViews() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WindowEffects(profile: Profile, fullscreen: Boolean) {
    val activity = LocalActivity.current ?: return
    val hide = profile.hidesSystemBars || fullscreen
    val edge = profile.display == DisplayMode.EDGE || profile.display == DisplayMode.KIOSK || fullscreen
    DisposableEffect(activity, hide, edge) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        activity.cutoutMode(edge)
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.cutoutMode(false)
        }
    }
    DisposableEffect(activity, profile.orientation) {
        activity.requestedOrientation = when (profile.orientation) {
            Orientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            Orientation.LOCKED -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(activity, profile.keepScreenOn) {
        activity.keepScreenOn(profile.keepScreenOn)
        onDispose { activity.keepScreenOn(false) }
    }
}

private fun Activity.cutoutMode(edge: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode = if (edge) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
    }
}

private fun Activity.keepScreenOn(on: Boolean) {
    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

@Composable
private fun BrowserToolbar(
    page: PageState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onSubmit: (String) -> Unit,
    onMenu: () -> Unit,
    onKeyboard: () -> Unit,
    modifier: Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val scheme = MaterialTheme.colorScheme
    BackHandler(enabled = editing) { editing = false }
    Surface(color = scheme.surfaceContainer) {
        Column(modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
            ) {
                if (!editing) {
                    IconButton(onClick = onBack, enabled = page.canGoBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    if (page.canGoForward) IconButton(onClick = onForward) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Forward") }
                }
                Surface(
                    onClick = {
                        text = TextFieldValue(page.url, TextRange(0, page.url.length))
                        focused = false
                        editing = true
                    },
                    shape = CircleShape,
                    color = scheme.surfaceContainerHighest,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(
                            when {
                                page.certificateError -> Icons.Rounded.LockOpen
                                page.secure -> Icons.Rounded.Lock
                                else -> Icons.Rounded.Info
                            },
                            null,
                            tint = if (page.certificateError) scheme.error else scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (editing) {
                            LaunchedEffect(Unit) { focus.requestFocus() }
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    editing = false
                                    onSubmit(text.text)
                                }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focus)
                                    .onFocusChanged {
                                        if (it.isFocused) focused = true else if (focused) editing = false
                                    },
                            )
                        } else {
                            Text(
                                displayUrl(page.url),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (editing) {
                    IconButton(onClick = { editing = false }) { Icon(Icons.Rounded.Close, "Cancel") }
                } else {
                    IconButton(onClick = onKeyboard) { Icon(Icons.Rounded.Keyboard, "Keyboard") }
                    IconButton(onClick = onReload) { Icon(if (page.loading) Icons.Rounded.Close else Icons.Rounded.Refresh, if (page.loading) "Stop" else "Reload") }
                    IconButton(onClick = onMenu) { Icon(Icons.Rounded.Tune, "Controls") }
                }
            }
            Box(Modifier.height(2.dp).fillMaxWidth()) {
                if (page.loading) LinearProgressIndicator(progress = { page.progress / 100f }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

fun displayUrl(url: String): String = url.removePrefix("https://").removePrefix("www.").removeSuffix("/")

@Composable
private fun SizeBadge(viewport: Viewport?, modifier: Modifier) {
    val v = viewport ?: return
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 6.dp),
    ) {
        Text(
            "${v.width} × ${v.height} · ${v.scale}%",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = Mono,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FrozenOverlay(onResume: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B2545).copy(alpha = 0.55f))
            .pointerInput(Unit) { detectDragGestures { _, _ -> } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Rounded.AcUnit, null, tint = Color(0xFF7FB7FF), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Page frozen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Timers, animations and rendering are paused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onResume) { Text("Resume") }
            }
        }
    }
}

@Composable
private fun FloatingHandle(onClick: () -> Unit) {
    val density = LocalDensity.current
    var offset by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = 44.dp
        val maxX = with(density) { (maxWidth - size).toPx() }
        val maxY = with(density) { (maxHeight - size).toPx() }
        val position = offset ?: Offset(maxX - with(density) { 12.dp.toPx() }, maxY * 0.6f)
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .size(size)
                .clip(CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val base = offset ?: position
                        offset = Offset((base.x + drag.x).coerceIn(0f, maxX), (base.y + drag.y).coerceIn(0f, maxY))
                    }
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_logo), "Controls", Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun KeyboardHelp(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val copy = rememberClipboard()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Keyboard, null) },
        title = { Text("A hardware keyboard is attached") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Android hides the on-screen keyboard while a hardware keyboard is connected, as on emulators with hw.keyboard=yes. " +
                        "Turn on \"Use on-screen keyboard\" in the keyboard settings, or run one of these over adb:",
                )
                CommandRow("Show it next to the hardware keyboard", Keyboard.ALLOW_COMMAND, copy)
                CommandRow("Or let this app switch it on by itself", Keyboard.grantCommand(context), copy)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onDismiss()
            }) { Text("Keyboard settings") }
        },
    )
}

@Composable
private fun CommandRow(label: String, command: String, copy: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Surface(onClick = { copy(command) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                Text(command, fontFamily = Mono, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ContentCopy, "Copy", Modifier.size(16.dp))
            }
        }
    }
}
