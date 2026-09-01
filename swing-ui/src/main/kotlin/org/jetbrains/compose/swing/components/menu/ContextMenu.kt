@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPopupMenu

/**
 * The menu the platform's popup gesture opens over the component [anchor] is bound to - a right-click,
 * or whatever else the look and feel gives that gesture, the keyboard binding included.
 *
 * The menu's items are a composition of their own, nested in this one, so they see the same state and
 * [androidx.compose.runtime.CompositionLocal]s as this call. They are composed when the gesture opens
 * the menu and released when it closes, and a menu on screen follows the state its items read.
 *
 * ```
 * val anchor = rememberPopupAnchor()
 * Label("Report", modifier = SwingModifier.popupAnchor(anchor))
 * ContextMenu(anchor) {
 *     MenuItem("Copy", onClick = { })
 * }
 * ```
 *
 * [onClose] reports the user closing the menu - selecting an item, pressing Escape, clicking away. A
 * menu another gesture replaces, or that this declaration takes away by leaving the composition, is
 * released without a close of its own.
 *
 * The component's own popup menu is captured while this declaration stands and given back when it goes.
 * One anchor carries one context menu: a second [ContextMenu] against the same anchor throws
 * `IllegalStateException` as its declaration enters the composition.
 *
 * For a menu the application opens itself, use [PopupMenu].
 *
 * @param anchor the handle naming the component the menu opens over, bound with [popupAnchor]. The
 *   component must be a [JComponent]; a bare [Component] carries no popup menu, and binding one throws
 *   `IllegalStateException`.
 * @param onOpen invoked when the menu has been put on screen.
 * @param onClose invoked when the user closes the menu.
 * @param content the composable menu tree shown in the popup.
 * @see javax.swing.JComponent.setComponentPopupMenu
 */
@Composable
public fun ContextMenu(
    anchor: PopupAnchor,
    onOpen: () -> Unit = {},
    onClose: () -> Unit = {},
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
) {
    ContextMenu(anchor, JPopupMenu::show, onOpen, onClose, content)
}

/**
 * Variant of [ContextMenu] that lets the caller decide how the populated [JPopupMenu] is presented,
 * instead of the default of showing it over the component at the trigger point.
 *
 * @param anchor the handle naming the component the menu opens over.
 * @param display invoked with the populated popup, the anchored component, and the trigger point
 *   (x, y in that component's coordinates) to present the popup.
 * @param onOpen invoked when the menu has been put on screen.
 * @param onClose invoked when the user closes the menu.
 * @param content the composable menu tree shown in the popup.
 */
@InternalSwingUiApi
@Composable
public fun ContextMenu(
    anchor: PopupAnchor,
    display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit,
    onOpen: () -> Unit = {},
    onClose: () -> Unit = {},
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
) {
    val parentContext = rememberCompositionContext()
    val host = remember(parentContext) { ContextMenuHost(parentContext) }
    // The callbacks and the body are read when a gesture opens the menu, not when this pass runs, so a
    // fresh lambda each pass reaches the already-attached host without reinstalling anything.
    host.display = display
    host.onOpen = onOpen
    host.onClose = onClose
    host.content = content
    DisposableEffect(anchor, host) {
        anchor.attach(host)
        onDispose { anchor.detach(host) }
    }
}

/**
 * The menu one [ContextMenu] declaration owns: while a component is bound it is that component's popup
 * menu and carries the trigger listener, and it gives the component's own menu back when the binding or
 * the declaration ends.
 */
private class ContextMenuHost(
    private val parentContext: CompositionContext,
) : AnchorAttachment {
    override val exclusive: Boolean get() = true

    var onOpen: () -> Unit = {}
    var onClose: () -> Unit = {}
    var display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit = JPopupMenu::show
    var content:
        @Composable @SwingMenuComposable
        () -> Unit = {}

    // The menu currently on screen, if any, so withdrawal can take it away: a menu the composition stops
    // declaring must not keep running, even though nothing dismissed it.
    private var open: MenuPopup? = null
    private var original: JPopupMenu? = null

    // The bound component's popup menu: the one menu every gesture reaches, so one report of an open and
    // of a close covers every gesture. The pointer gesture reaches it through the listener below, and the
    // keyboard gesture through the binding the look and feel gives it. It stands for the menu across
    // gestures; the menu a gesture puts on screen is a composition of its own, released when that menu
    // closes.
    private val popup =
        object : JPopupMenu() {
            override fun show(
                invoker: Component,
                x: Int,
                y: Int,
            ): Unit = present(invoker, x, y)
        }

    private fun present(
        invoker: Component,
        x: Int,
        y: Int,
    ) {
        // A predecessor still held here never reached the screen - the display below may decline this one
        // too - so it is taken down rather than left running unreachable.
        open?.close()
        val menu = MenuPopup(parentContext, content, onClosed = ::onMenuClosed)
        open = menu
        // The open is reported once the presentation has put the menu on screen, so a presentation that
        // refuses the menu reports no open.
        display(menu.popup, invoker, x, y)
        onOpen()
    }

    private fun onMenuClosed(menu: MenuPopup) {
        // Only the menu this host still tracks clears the field. Putting a popup on screen hides whatever
        // popup the toolkit's selection path already held, in the same call and before it returns, so a
        // close landing here can be one for a menu already replaced. Clearing the field then would leave
        // the replacement running when the declaration withdraws.
        if (open === menu) open = null
        onClose()
    }

    private val listener =
        object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent): Unit = maybeShow(e)

            override fun mouseReleased(e: MouseEvent): Unit = maybeShow(e)

            private fun maybeShow(e: MouseEvent) {
                // isPopupTrigger is set on press on some platforms and on release on others; checking both
                // events is the cross-platform-correct gesture detection.
                if (!e.isPopupTrigger) return
                // A look and feel that opens a component's popup menu on the gesture itself consumes the
                // event; this listener performs the gesture for one that does not, and one menu opens
                // either way.
                if (e.isConsumed) return
                popup.show(e.component, e.x, e.y)
            }
        }

    override fun onBound(component: Component) {
        check(component is JComponent) {
            "A context menu needs a JComponent to carry it, and ${component.javaClass.name} is not one. " +
                "Bind the anchor to a Swing component rather than a bare AWT one."
        }
        // getComponentPopupMenu() resolves an inherited ancestor menu when the component has none of its
        // own; capturing that would pin the ancestor's menu onto this component as its own on withdrawal,
        // instead of restoring "no own menu". Clearing the flag for the read is what tells the two apart,
        // since a component may both inherit and carry one of its own.
        val inherits = component.inheritsPopupMenu
        component.inheritsPopupMenu = false
        original = component.componentPopupMenu
        component.inheritsPopupMenu = inherits
        component.componentPopupMenu = popup
        component.addMouseListener(listener)
    }

    override fun onUnbound(component: Component) {
        open?.close()
        open = null
        component.removeMouseListener(listener)
        (component as? JComponent)?.componentPopupMenu = original
        original = null
    }
}
