@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.setContentAsInteropHost
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JRootPane

/**
 * Declares the glass pane of the window whose content this is composed in.
 *
 * This is declared on [WindowScope], the receiver of the content of a [Window] and of a [Dialog], so it
 * is only available where there is a window to carry the pane.
 *
 * A glass pane is the sheet above everything else in the window: it covers the whole window and is
 * transparent where [content] paints nothing - which is what makes it the place for a drag-and-drop hint,
 * a progress veil, or anything else drawn over the window rather than in it. A mouse event reaches the
 * deepest component under the pointer that listens for it, so a click over part of [content] that
 * listens - a button in the overlay - goes there, and a click anywhere else goes on to the window's
 * content underneath. To keep the window's content out of reach, fill the pane with content that listens
 * for the mouse. [content] fills the pane, so a layout composable inside it places what the overlay is
 * made of:
 *
 * ```
 * Window(onCloseRequest = ::exitApplication) {
 *     if (loading) {
 *         GlassPane {
 *             Panel(PanelLayout.GridBag) {
 *                 item { ProgressBar(value = 0, indeterminate = true) }
 *             }
 *         }
 *     }
 *     Editor()
 * }
 * ```
 *
 * The content shares the surrounding composition, so it shows the current state the way any other
 * composable does. The pane is over the window for as long as this is in the composition; once this
 * leaves, the window carries the glass pane it carried before, showing it as it was shown - so an
 * overlay that comes and goes is an ordinary `if` around the call.
 *
 * A window carries one glass pane, so one declaration serves a window: put the choice of overlay inside
 * the declaration rather than composing a second one for the same window, which fails and leaves the
 * window the pane it carried.
 *
 * The same sheet over one wrapped component rather than a whole window is
 * `LayerScope.GlassPane`.
 *
 * @param content the composable content the glass pane shows over the window.
 * @see javax.swing.JRootPane.setGlassPane
 */
@Composable
public fun WindowScope.GlassPane(
    content:
        @Composable
        () -> Unit,
) {
    // The content is hosted as a child of this composition, so state around the GlassPane reaches the
    // overlay and its callbacks, and content that merely changes flows in through the handle the
    // recomposition refreshes.
    val currentContent by rememberUpdatedState(content)
    val parentContext = rememberCompositionContext()
    val scope = this

    DisposableEffect(rootPane) {
        val serving = scope.declaredGlassPane
        if (serving != null) {
            // Answering this declaration ends the composition both of them belong to, which is not
            // itself a withdrawal of the one already serving the window, so it is withdrawn here: the
            // window - which a caller may own and keep - is handed back the pane it carried before, and
            // carries no record to refuse the next declaration reaching it.
            serving.withdraw()
            scope.declaredGlassPane = null
            error(
                "Two GlassPane { } declarations are composed in this window at once, and a window " +
                    "carries one glass pane: the second would take the window from the first, leaving " +
                    "that declaration composed with nothing of it on the window. Declare one " +
                    "GlassPane { } per window and put the choice inside it, or branch so that only one " +
                    "of them is composed at a time.",
            )
        }

        val pane = WindowGlassPane()
        val displaced = scope.rootPane.glassPane
        val declaration =
            WindowDecoration(
                payload = PaneVisibility(pane, visible = true),
                displaced = PaneVisibility(displaced, visible = displaced.isVisible),
                install = { (component, visible) -> scope.rootPane.installGlassPane(component, visible) },
            )
        val handle =
            pane.setContentAsInteropHost(parentContext) {
                currentContent()
            }
        scope.declaredGlassPane = declaration
        declaration.serve()

        onDispose {
            declaration.withdraw()
            scope.declaredGlassPane = null
            handle.dispose()
        }
    }
}

/** A glass pane [WindowDecoration] installs, and whether it is to be shown once installed. */
private data class PaneVisibility(
    val component: Component,
    val visible: Boolean,
)

/**
 * The pane a [GlassPane] fills, transparent where its content paints nothing, the way a root pane's own
 * glass pane is, where a plain panel would be opaque and would hide the window.
 */
private class WindowGlassPane : JPanel(BorderLayout()) {
    init {
        isOpaque = false
    }
}

/**
 * Puts [glassPane] over this root pane at the given [visible], and gives it the window's bounds: a root
 * pane sizes the glass pane to the whole window as it lays itself out, so that pass is run here rather
 * than only asked for. Asking for it answers no earlier than the end of the current event, and answers
 * not at all while the window is not showing, either of which leaves the pane standing at no size over
 * the window it is meant to cover.
 *
 * The revalidate stays, for the pane's own content: that content is composed into the pane while the
 * pane has no parent, and a revalidate asked for then is dropped, so the pass laying it out is asked for
 * here once the pane is on the window.
 *
 * A root pane hands an arriving glass pane the visibility of the one it replaces, so the visibility this
 * pane is to be carried at is stated right after the swap.
 */
private fun JRootPane.installGlassPane(
    glassPane: Component,
    visible: Boolean,
) {
    this.glassPane = glassPane
    glassPane.isVisible = visible
    doLayout()
    revalidate()
}
