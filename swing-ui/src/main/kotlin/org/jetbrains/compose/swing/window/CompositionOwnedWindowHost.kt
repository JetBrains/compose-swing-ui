package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.core.KeepEnclosingApplicationAlive
import org.jetbrains.compose.swing.setContentAsInteropHost
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.RootPaneContainer

/**
 * Wires a composition-owned top-level AWT [peer] (a [javax.swing.JFrame] or [javax.swing.JDialog])
 * into the enclosing composition: it forwards the window-closing gesture to [onCloseRequest], installs
 * the geometry write-back, hosts [content] as a child of the enclosing composition - under a
 * [WindowScope] standing for [peer] - and pushes the reactive chrome ([title], [resizable],
 * [alwaysOnTop], [iconImage], [minimumSize]) and geometry onto the peer.
 *
 * [peer] itself is reactive. A caller that must rebuild its peer - because a property AWT accepts only
 * on a window that is not yet realized changed - hands the replacement in, and the wiring follows: the
 * previous peer loses its content, its listeners and its own peer, and the replacement is wired and
 * hosted from scratch.
 *
 * The shape of the two directions matches the two-way geometry model: [position]/[width]/[height] are
 * read here in the composable body (so mutating them recomposes and re-applies), while [setPosition]
 * and [setSize] carry user-driven moves and resizes back into the hoisted state.
 *
 * Everything that is specific to one kind of peer is threaded in as a lambda: [installExtras] alongside
 * the wiring this function installs, [applyExtras] at the tail of the reactive [SideEffect] (after
 * geometry, so it can flip visibility once the peer is sized and positioned), and [disposePeer] at the
 * very end of teardown, so a caller can interleave its own steps (for example, hiding a modal dialog
 * before it is disposed).
 *
 * @param installExtras registers any additional listeners in the same [DisposableEffect] as the wiring
 *   installed here, and returns their removal. It runs once per peer, while the state a listener writes
 *   into is declared afresh on every recomposition, so a listener it installs has to reach that state
 *   through a handle the recomposition refreshes - `rememberUpdatedState`. A listener that captures the
 *   state declared when the peer was wired keeps writing into the one the caller has moved on from,
 *   instead of the one a caller that hoists its state somewhere else has moved to.
 */
@Composable
internal fun CompositionOwnedWindowHost(
    peer: Window,
    onCloseRequest: () -> Unit,
    title: String,
    resizable: Boolean,
    alwaysOnTop: Boolean,
    iconImage: Image?,
    minimumSize: Dimension?,
    position: WindowPosition,
    width: Int,
    height: Int,
    setPosition: (WindowPosition) -> Unit,
    setSize: (width: Int, height: Int) -> Unit,
    appliedGeometry: AppliedGeometry,
    installExtras: () -> () -> Unit,
    applyExtras: () -> Unit,
    disposePeer: () -> Unit,
    content: @Composable WindowScope.() -> Unit,
) {
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentContent by rememberUpdatedState(content)
    // The geometry write-back listener is installed once per peer, so it reads its writers from here.
    val currentSetPosition by rememberUpdatedState(setPosition)
    val currentSetSize by rememberUpdatedState(setSize)

    KeepEnclosingApplicationAlive()

    // Capture the enclosing composition context (the application/window composition) here in the
    // composable body, NOT inside the DisposableEffect: the peer's content pane is a detached top-level
    // peer, so the Swing-tree walk from it finds no parent. Threading this context through explicitly
    // makes the peer content a CHILD of the enclosing composition, so app-scope state and
    // CompositionLocals flow into the content. This is the deliberate "preserve app->window flow"
    // choice: a window created declaratively under application { } stays a child of the enclosing
    // composition rather than spinning up its own window-local recomposer.
    val parentContext = rememberCompositionContext()

    val container = peer as RootPaneContainer

    // The scope the content is given stands for this peer, so a declaration the content makes on it - a
    // menu bar - reaches the window that content is in. Tied to the peer: a replacement peer hands its
    // content a scope of its own.
    val scope: WindowScope = remember(peer) { WindowScope.of(container.rootPane) }

    // Keyed on the peer: when a caller replaces it, the effect tears the old peer's wiring down (and
    // disposes it) before the new peer is listened to and hosted.
    DisposableEffect(peer) {
        val windowListener =
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    currentOnCloseRequest()
                }
            }
        peer.addWindowListener(windowListener)

        val geometryListener =
            peer.installGeometryWriteBack(
                applied = appliedGeometry,
                setPosition = { currentSetPosition(it) },
                setSize = { newWidth, newHeight -> currentSetSize(newWidth, newHeight) },
            )

        val removeExtras = installExtras()

        val handle =
            container.contentPane.setContentAsInteropHost(parentContext) {
                CompositionLocalProvider(LocalWindow provides peer) {
                    scope.currentContent()
                }
            }

        onDispose {
            removeExtras()
            handle.dispose()
            peer.removeComponentListener(geometryListener)
            peer.removeWindowListener(windowListener)
            disposePeer()
        }
    }

    // Reactive params: re-applied whenever the corresponding argument changes across recomposition.
    // Effect bodies run on the composition's Swing dispatcher (the EDT), so these mutations are
    // EDT-safe. Geometry is applied before [applyExtras] so the peer is sized and positioned before its
    // visibility (and, for a frame, its extended state) is flipped.
    SideEffect {
        peer.applyChrome(title, resizable)
        peer.applyAlwaysOnTop(alwaysOnTop)
        peer.applyIconImage(iconImage)
        // The floor is installed before the geometry so a size below it is clamped as it arrives rather
        // than the peer being sized twice: AWT raises a size below the floor whichever way round these
        // two land, both when the size is set and when the floor is.
        peer.applyMinimumSize(minimumSize)
        peer.applyGeometry(position, width, height, appliedGeometry)
        applyExtras()
    }
}

/**
 * Pushes the declared [title] and [resizable] onto this peer when they differ from what it already
 * carries. Both [Frame] and [Dialog] declare these accessors independently, with no shared supertype,
 * so the write dispatches on the concrete peer type.
 */
private fun Window.applyChrome(
    title: String,
    resizable: Boolean,
) {
    when (this) {
        is Frame -> {
            if (this.title != title) this.title = title
            if (isResizable != resizable) isResizable = resizable
        }

        is Dialog -> {
            if (this.title != title) this.title = title
            if (isResizable != resizable) isResizable = resizable
        }
    }
}

/**
 * Pushes the declared [alwaysOnTop] onto this window when it differs from what the window already
 * carries. A platform with no always-on-top support drops the request while still recording it, so the
 * write is skipped there and the window keeps reporting the state it actually has.
 */
private fun Window.applyAlwaysOnTop(alwaysOnTop: Boolean) {
    if (isAlwaysOnTopSupported && isAlwaysOnTop != alwaysOnTop) isAlwaysOnTop = alwaysOnTop
}

/**
 * Pushes the declared [iconImage] onto this window when it differs from the icon the window already
 * carries. A null [iconImage] clears the icon, restoring the platform default.
 */
private fun Window.applyIconImage(iconImage: Image?) {
    if (iconImages.firstOrNull() != iconImage) setIconImage(iconImage)
}

/**
 * Pushes the declared [minimumSize] onto this window when it differs from the floor the window already
 * carries. A null [minimumSize] releases the floor, so the window reports the minimum size its layout
 * computes; reading that computed size is itself a layout pass, hence it is only read back for
 * comparison once a floor has been set.
 */
private fun Window.applyMinimumSize(minimumSize: Dimension?) {
    if (minimumSize == null) {
        if (isMinimumSizeSet) this.minimumSize = null
    } else if (!isMinimumSizeSet || this.minimumSize != minimumSize) {
        // AWT keeps the very instance it is handed, so the floor is installed from a copy: mutating the
        // declared dimension afterwards must not move the floor behind the composition's back.
        this.minimumSize = Dimension(minimumSize)
    }
}
