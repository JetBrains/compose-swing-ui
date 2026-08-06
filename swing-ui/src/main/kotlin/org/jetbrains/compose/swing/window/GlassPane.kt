@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.node.LocalSlotAttachment
import org.jetbrains.compose.swing.node.LocalSwingConstraint
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
 * A glass pane is the sheet above everything else in the window: it covers the whole window, it is
 * transparent where [content] paints nothing, and while it is shown the window's mouse events reach it -
 * which is what makes it the place for a drag-and-drop hint, a progress veil, or anything else drawn over
 * the window rather than in it. [content] fills the pane, so a layout composable inside it places what
 * the overlay is made of:
 *
 * ```
 * Window(onCloseRequest = ::exitApplication) {
 *     if (loading) {
 *         GlassPane {
 *             GridBagPanel {
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
            error(
                "Two GlassPane { } declarations are composed in this window at once, and a window " +
                    "carries one glass pane: the second would take the window from the first, leaving " +
                    "that declaration composed with nothing of it on the window. Declare one " +
                    "GlassPane { } per window and put the choice inside it, or branch so that only one " +
                    "of them is composed at a time.",
            )
        }

        val declaration = DeclaredGlassPane(scope)
        val handle =
            declaration.pane.setContentAsInteropHost(parentContext) {
                // The overlay joins the composition enclosing this call, so it would otherwise inherit
                // the slot attachment/constraint of whatever hosts that call site. Reset both: the
                // overlay's nodes are ordinary children of the glass pane itself.
                CompositionLocalProvider(
                    LocalSlotAttachment provides null,
                    LocalSwingConstraint provides null,
                ) {
                    currentContent()
                }
            }
        declaration.serve()

        onDispose {
            declaration.withdraw()
            handle.dispose()
        }
    }
}

/**
 * The glass pane one [GlassPane] declaration puts on [WindowScope.rootPane], together with the pane that
 * root pane carried before and the visibility it carried it at.
 *
 * A window carries one glass pane, so whether one already serves it is read off [scope]'s own
 * [WindowScope.declaredGlassPane] rather than off the Swing side: that field is this declaration's sole
 * bookkeeping, so a pane already in place answers for itself.
 */
internal class DeclaredGlassPane(
    private val scope: WindowScope,
) {
    /**
     * The pane this declaration puts on the window; the declared content fills it.
     *
     * Transparent, the way a root pane's own glass pane is, so the window shows through wherever the
     * content paints nothing.
     */
    val pane: JPanel = JPanel(BorderLayout()).apply { isOpaque = false }

    private val displaced: Component = scope.rootPane.glassPane
    private val displacedVisible: Boolean = displaced.isVisible
    private var serving: Boolean = false

    /** Puts [pane] over the window, shown, and records this declaration as the one serving it. */
    fun serve() {
        serving = true
        scope.declaredGlassPane = this
        scope.rootPane.installGlassPane(pane, visible = true)
    }

    /**
     * Hands the window back the pane it carried before [serve], shown as it was shown then. Does nothing
     * unless this declaration is the one serving, so withdrawing what has already been withdrawn leaves
     * the window alone.
     */
    fun withdraw() {
        if (!serving) return
        serving = false
        scope.declaredGlassPane = null
        scope.rootPane.installGlassPane(displaced, visible = displacedVisible)
    }
}

/**
 * Puts [glassPane] over this root pane at the given [visible], and asks for the layout pass the change
 * needs: a root pane sizes the glass pane to the whole window as it lays itself out, so an arriving pane
 * is given those bounds once the root pane has been laid out again.
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
    revalidate()
}
