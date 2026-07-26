package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.Dimension
import java.awt.Image
import javax.swing.JFrame
import javax.swing.WindowConstants

/**
 * Composes a Window (JFrame) with the given content.
 *
 * The window content runs as part of the enclosing application composition: state held in the
 * application scope and any [androidx.compose.runtime.CompositionLocal] provided above the window
 * flow into [content], and the content keeps recomposing while the window is shown.
 *
 * [content] receives the window as its [WindowScope]: what the window carries besides its content - its
 * [MenuBar] - is declared on that scope.
 *
 * The [title], [visible], [resizable], [alwaysOnTop], [iconImage] and [minimumSize] arguments are
 * reactive: changing any of them in a recomposition updates the realized window accordingly. Geometry
 * and the extended state are driven by [state], which is two-way: assigning to
 * [WindowState.position]/[WindowState.size]/[WindowState.extendedState] repositions, resizes,
 * maximizes, minimizes or restores the window, and a user driving the same change through the window
 * system writes the new value back into [state].
 *
 * [undecorated] is reactive too, at a higher price: AWT only accepts decorations on a window that is not
 * yet realized, so changing it releases the window peer and builds a replacement. The content is
 * re-hosted in the new peer - its composition, and any state remembered in it, starts over - while the
 * geometry and extended state held in [state] are re-applied to the replacement. A look and feel that
 * draws the window decorations itself (see [JFrame.setDefaultLookAndFeelDecorated]) draws them on the
 * replacement too, whatever this argument declares.
 *
 * @param onCloseRequest callback to be called when the user attempts to close the window
 * @param state the hoistable, observable geometry (position and size) and extended state of the
 *   window
 * @param title the title of the window
 * @param visible whether the window should be visible
 * @param resizable whether the window can be resized
 * @param alwaysOnTop whether the window stays above other windows; ignored on platforms that do not
 *   support an always-on-top window
 * @param iconImage the image shown as the window's icon, or null for the platform default
 * @param minimumSize the smallest size the window can take, or null to leave the floor to the window's
 *   layout; a declared or user-driven size below the floor is raised to it
 * @param undecorated whether the window is shown without its platform decorations (title bar and
 *   border)
 * @param content the composable content of the window, receiving the window as its [WindowScope]
 */
@Composable
public fun Window(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    title: String = "",
    visible: Boolean = true,
    resizable: Boolean = true,
    alwaysOnTop: Boolean = false,
    iconImage: Image? = null,
    minimumSize: Dimension? = null,
    undecorated: Boolean = false,
    content: @Composable WindowScope.() -> Unit,
) {
    // Decorations are chosen at construction: JFrame.setUndecorated is rejected once the peer is
    // displayable, so a change of the declaration builds a new frame rather than writing onto the
    // realized one. Only an explicit undecorated declaration is written, so a frame that decorates
    // itself through its look and feel keeps both the decoration style and the undecorated flag that
    // pairing needs.
    val frame =
        remember(undecorated) {
            JFrame().also {
                if (undecorated) it.isUndecorated = true
                it.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            }
        }

    // Holds the geometry and extended state that are currently in sync between [state] and the realized
    // frame. Shared by the apply and the write-back listeners so the two directions never fight, and
    // tied to the frame it describes: a replacement frame starts out of sync and is given the geometry
    // and extended state [state] holds.
    val appliedGeometry = remember(frame) { AppliedGeometry() }

    val extendedState = state.extendedState

    // The extended-state write-back listener is installed once per frame, so it reads the state here.
    val currentState by rememberUpdatedState(state)

    CompositionOwnedWindowHost(
        peer = frame,
        onCloseRequest = onCloseRequest,
        title = title,
        resizable = resizable,
        alwaysOnTop = alwaysOnTop,
        iconImage = iconImage,
        minimumSize = minimumSize,
        position = state.position,
        width = state.width,
        height = state.height,
        setPosition = { state.position = it },
        setSize = { width, height ->
            state.width = width
            state.height = height
        },
        appliedGeometry = appliedGeometry,
        installExtras = {
            val extendedStateListener =
                frame.installExtendedStateWriteBack(
                    applied = appliedGeometry,
                    setExtendedState = { currentState.extendedState = it },
                )
            val removeExtendedStateListener = { frame.removeWindowStateListener(extendedStateListener) }
            removeExtendedStateListener
        },
        applyExtras = {
            // The extended state is applied before the visibility flip so the window appears already
            // maximized, minimized or restored.
            frame.applyExtendedState(extendedState, appliedGeometry)
            if (frame.isVisible != visible) frame.isVisible = visible
        },
        disposePeer = { frame.dispose() },
        content = content,
    )
}
