@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.modifier.interaction.FirstShowing
import java.awt.Component
import javax.swing.JPopupMenu

/**
 * A menu the application opens itself - a drop-down button, an overflow menu, a shortcut - shown over
 * the component [anchor] is bound to.
 *
 * The menu is open while [expanded] is `true`. Closing is the toolkit's own doing and cannot be
 * refused: adopt the close [onDismiss] reports by setting [expanded] back to `false`, or the next `true`
 * declares nothing new and the menu stays shut.
 *
 * ```
 * val anchor = rememberPopupAnchor()
 * var expanded by remember { mutableStateOf(false) }
 * Button("File", onClick = { expanded = true }, modifier = SwingModifier.popupAnchor(anchor))
 * PopupMenu(anchor, expanded, onDismiss = { expanded = false }) {
 *     MenuItem("Open", onClick = { })
 * }
 * ```
 *
 * The menu's items are a composition of their own, nested in this one, so they see the same state and
 * [androidx.compose.runtime.CompositionLocal]s as this call. They are composed when the menu opens and
 * released when it closes, and a menu on screen follows the state its items read.
 *
 * A menu declared open against an anchor that is not bound yet - or bound to a component not yet on
 * screen - waits and opens once there is somewhere to anchor to.
 *
 * For the menu a right-click opens, use [ContextMenu].
 *
 * @param anchor the handle naming the component the menu opens over, bound with
 *   [popupAnchor].
 * @param expanded whether the menu is open.
 * @param onDismiss invoked when the user closes the menu.
 * @param content the composable menu tree shown in the menu.
 * @see javax.swing.JPopupMenu.show
 */
@Composable
public fun PopupMenu(
    anchor: PopupAnchor,
    expanded: Boolean,
    onDismiss: () -> Unit,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
) {
    PopupMenu(anchor, expanded, JPopupMenu::show, onDismiss, content)
}

/**
 * Variant of [PopupMenu] that lets the caller decide how the populated [JPopupMenu] is presented,
 * instead of the default of showing it at the anchor point under the bound component.
 *
 * @param anchor the handle naming the component the menu opens over.
 * @param expanded whether the menu is open.
 * @param display invoked with the populated popup, the anchored component, and the anchor point
 *   (x, y in that component's coordinates) to present the popup.
 * @param onDismiss invoked when the user closes the menu.
 * @param content the composable menu tree shown in the menu.
 */
@InternalSwingUiApi
@Composable
public fun PopupMenu(
    anchor: PopupAnchor,
    expanded: Boolean,
    display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit,
    onDismiss: () -> Unit,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
) {
    val parentContext = rememberCompositionContext()
    val host = remember(parentContext) { PopupMenuHost(parentContext) }
    // See ContextMenu for why a fresh lambda each pass is safe.
    host.display = display
    host.onDismiss = onDismiss
    host.content = content
    DisposableEffect(anchor, host) {
        anchor.attach(host)
        onDispose { anchor.detach(host) }
    }
    // After the anchor has been told about this menu, so a menu declared open on its first pass finds
    // the host attached and opens against whatever component the anchor already names.
    SideEffect { host.apply(expanded) }
}

/**
 * The menu one [PopupMenu] declaration owns: it opens and closes to match the declaration, waits for a
 * component to open against, and takes an open menu away when the declaration goes.
 */
private class PopupMenuHost(
    private val parentContext: CompositionContext,
) : AnchorAttachment {
    var onDismiss: () -> Unit = {}
    var display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit = JPopupMenu::show
    var content:
        @Composable @SwingMenuComposable
        () -> Unit = {}

    private var component: Component? = null
    private var open: MenuPopup? = null
    private val showing = FirstShowing()

    // The declaration this host has applied, so only a change of it opens or closes the menu: a menu the
    // user dismissed is not reopened by an unrelated recomposition. It outlives an unbind, so an anchor
    // bound to a new component reopens a menu the declaration still holds open.
    private var applied: Boolean? = null

    /** Opens or closes the menu to match [expanded]. */
    fun apply(expanded: Boolean) {
        if (applied == expanded) return
        applied = expanded
        if (expanded) show() else close()
    }

    override fun onBound(component: Component) {
        this.component = component
        if (applied == true) show()
    }

    override fun onUnbound(component: Component) {
        if (this.component !== component) return
        close()
        this.component = null
    }

    // A menu declared open before there is a component, or against one not showing yet, waits: a popup is
    // anchored to its invoker, and a component that is not showing has no place on screen to anchor to.
    private fun show() {
        val invoker = component ?: return
        showing.await(invoker) { present(invoker) }
    }

    private fun present(invoker: Component) {
        val menu = MenuPopup(parentContext, content, onClosed = ::onMenuClosed)
        open = menu
        // Anchored under the component: a menu opened from a control belongs below it, the way a
        // drop-down button's menu does.
        display(menu.popup, invoker, 0, invoker.height)
    }

    private fun onMenuClosed(menu: MenuPopup) {
        // Only the menu this host still tracks clears the field - see ContextMenuHost.onMenuClosed for
        // the close that lands here for a menu already gone.
        if (open === menu) open = null
        onDismiss()
    }

    private fun close() {
        showing.cancel()
        open?.close()
        open = null
    }
}
