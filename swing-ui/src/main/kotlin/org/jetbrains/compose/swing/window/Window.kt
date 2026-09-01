@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.awt.Image
import javax.swing.JFrame
import javax.swing.WindowConstants

/**
 * A top-level application window, realized as a `JFrame`: it shows [content], holds its geometry and
 * extended state in [state], and reports the user's attempt to close it to [onCloseRequest].
 *
 * The close gesture is controlled: it invokes [onCloseRequest] and closes nothing, so the window stays
 * on screen until the caller answers - by declaring [visible] `false`, or by stopping declaring the
 * window at all, which is what releases its peer.
 *
 * The window content runs as part of the enclosing application composition, and keeps recomposing while
 * the window is shown.
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
 *   window; by default one this window keeps to itself, which leaves the placement to the platform,
 *   sizes the window to its content, and starts it neither maximized nor minimized
 * @param title the title of the window, empty by default, matching a freshly constructed `JFrame`
 * @param visible whether the window should be visible; `true` by default, so declaring a window shows
 *   it, and `false` hides the window while keeping its content composed
 * @param resizable whether the window can be resized; `true` by default, matching a freshly
 *   constructed `JFrame`
 * @param alwaysOnTop whether the window stays above other windows; ignored on platforms that do not
 *   support an always-on-top window, and `false` by default, so the window takes its turn in the
 *   platform's stacking order
 * @param iconImage the image shown as the window's icon, or null for the platform default; the
 *   windowing system may show it in several places at sizes of its own, or show none at all
 * @param minimumSize the smallest size the window can take, or null to leave the floor to the window's
 *   layout; a declared size below the floor is raised to it, while holding the user's own resizing to
 *   the floor is platform-dependent
 * @param undecorated whether the window is shown without its platform decorations (title bar and
 *   border); `false` by default, so the window is shown with them
 * @param content the composable content of the window, receiving the window as its [WindowScope]
 * @see javax.swing.JFrame
 */
@Composable
public fun Window(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    title: @Nls String = "",
    visible: Boolean = true,
    resizable: Boolean = true,
    alwaysOnTop: Boolean = false,
    iconImage: Image? = null,
    minimumSize: Dimension? = null,
    undecorated: Boolean = false,
    content: @Composable WindowScope.() -> Unit,
) {
    // Only an explicit undecorated declaration is written, so a frame that decorates itself through its
    // look and feel keeps both the decoration style and the undecorated flag that pairing needs.
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
        applyDeclaredGeometry = { frame.applyGeometry(state.position, state.width, state.height, appliedGeometry) },
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
            frame.applyExtendedState(state.extendedState, appliedGeometry)
            if (frame.isVisible != visible) frame.isVisible = visible
        },
        disposePeer = { frame.dispose() },
        content = content,
    )
}
