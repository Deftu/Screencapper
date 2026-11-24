@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.deftu.screencapper.screenshot.Screenshot
import org.polyfrost.compose.host.controller.ControllerAxes
import org.polyfrost.compose.host.controller.controllerArrows
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ProvideThemeVariant
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.rememberImageBitmap
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

private const val FULLSCREEN_WIDTH = 2560
private const val BACKDROP_ALPHA = 0.9f
private val GUTTER = 72.dp

private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset.Zero
}

@Composable
internal fun FullscreenViewer(screenshot: Screenshot, list: List<Screenshot>, onNavigate: (Screenshot) -> Unit, onClose: () -> Unit, onEdit: () -> Unit) {
    val index = list.indexOfFirst { it.id == screenshot.id }
    val previous = list.getOrNull(index - 1)
    val next = list.getOrNull(index + 1)
    val latestPrevious by rememberUpdatedState(previous)
    val latestNext by rememberUpdatedState(next)
    val latestNavigate by rememberUpdatedState(onNavigate)
    val focus = remember { FocusRequester() }
    val fade = remember { Animatable(0f) }
    val zoom = rememberZoomState(screenshot.file)

    EscapeCloses(onClose)
    LaunchedEffect(Unit) { fade.animateTo(1f, Motion.finiteSpec(tween(Motion.standard))) }

    Popup(WindowOrigin, null, PopupProperties(focusable = true, clippingEnabled = false)) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        ProvideThemeVariant(dark = true) {
            val theme = OneConfigTheme.current
            val spacing = theme.spacing
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade.value }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val target = when (event.key) {
                            Key.DirectionLeft -> latestPrevious
                            Key.DirectionRight -> latestNext
                            else -> null
                        } ?: return@onKeyEvent false
                        latestNavigate(target)
                        true
                    }
                    .controllerZoom(zoom)
                    .controllerArrows(ControllerAxes.Horizontal)
                    .focusRequester(focus)
                    .focusable(),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(theme.scrimColor.copy(alpha = BACKDROP_ALPHA))
                        .notFocusable()
                        .onTap(onClose),
                )
                FullscreenFrame(screenshot, zoom, Modifier.fillMaxSize().padding(GUTTER))
                if (previous != null) {
                    IconButton(
                        "left-arrow", "Previous screenshot", { onNavigate(previous) },
                        Modifier.align(Alignment.CenterStart).padding(spacing.l),
                        style = IconButtonStyle.Overlay, size = ControlSize.Large,
                    )
                }
                if (next != null) {
                    IconButton(
                        "right-arrow", "Next screenshot", { onNavigate(next) },
                        Modifier.align(Alignment.CenterEnd).padding(spacing.l),
                        style = IconButtonStyle.Overlay, size = ControlSize.Large,
                    )
                }
                Row(Modifier.align(Alignment.TopEnd).padding(spacing.l), horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                    IconButton("paintbrush", "Edit", onEdit, style = IconButtonStyle.Overlay, size = ControlSize.Large)
                    IconButton("close", "Exit full screen", onClose, style = IconButtonStyle.Overlay, size = ControlSize.Large)
                }
                if (index >= 0) {
                    Badge(
                        if (list.size > 1) "${screenshot.file.nameWithoutExtension} · ${index + 1} of ${list.size}" else screenshot.file.nameWithoutExtension,
                        Modifier.align(Alignment.BottomCenter).padding(spacing.xl),
                        overImage = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenFrame(screenshot: Screenshot, zoom: ZoomState, modifier: Modifier) {
    val loaded = rememberImageBitmap(screenshot.file.toPath(), FULLSCREEN_WIDTH)
    val size = rememberImageSize(screenshot)
    val aspect = loaded?.let { it.width.toFloat() / it.height } ?: size?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val frameWidth = minOf(maxWidth, maxHeight * aspect)
        Box(
            Modifier
                .size(frameWidth, frameWidth / aspect)
                .themedSurface(SurfaceKind.Card)
                .swallowPresses(),
        ) {
            ZoomableImage(screenshot.file, size, intArrayOf(VIEWER_WIDTH, FULLSCREEN_WIDTH), Modifier.fillMaxSize(), zoom)
        }
    }
}

private fun Modifier.swallowPresses(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
