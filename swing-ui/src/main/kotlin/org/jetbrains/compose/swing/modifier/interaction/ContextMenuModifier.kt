@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPopupMenu

/**
 * Attaches a context menu to the target component, shown when the user requests a popup over it: with
 * the pointer (right-click on most platforms, the platform popup gesture elsewhere) and from the
 * keyboard, through the binding the look and feel gives a component's popup menu.
 *
 * The menu becomes the target's own popup menu, which is what puts it on both gestures, so the target
 * must be a `JComponent`; the popup menu it carried before is restored when the declaration leaves the
 * chain. A component has one popup menu, so the last `contextMenu` in a chain owns it.
 *
 * The [content] is the same menu tree used by a menu bar - `Menu`, `MenuItem`, `CheckBoxMenuItem`,
 * `RadioButtonMenuItem`, `MenuSeparator`. Each time the user triggers the popup, [content] is composed
 * fresh into a [JPopupMenu] shown at the point the gesture asks for; selecting an item runs that item's
 * callback. The menu reads composition state, so the items it shows reflect the current state at the
 * moment the popup opens, and an item's callback updates state like any other composable callback.
 *
 * The menu shares the surrounding composition's recomposition scope and
 * [androidx.compose.runtime.CompositionLocal]s, so state hoisted around the modified component is
 * visible to the menu items and to their callbacks.
 *
 * [onOpen] reports the menu reaching the screen and [onClose] the user closing it - selecting an item,
 * pressing Escape, clicking away. A menu reports one open and one close, whichever gesture opened it
 * and whichever way it went away. The close is the toolkit's own doing and cannot be refused, so
 * [onClose] reports a close rather than asking for one.
 *
 * Call this `@Composable` builder where you build the component's modifier chain, and pass a fresh
 * [content] lambda each recomposition. The popup is dismissed and its resources released when the user
 * closes it.
 *
 * For a menu the application opens itself - a drop-down button, an overflow menu, a shortcut - use
 * [popupMenu].
 *
 * @param onOpen invoked when the menu has been put on screen.
 * @param onClose invoked when the user closes the menu.
 * @param content the composable menu tree shown in the popup.
 * @see javax.swing.JComponent.setComponentPopupMenu
 */
@Composable
public fun SwingModifier.contextMenu(
    onOpen: () -> Unit = {},
    onClose: () -> Unit = {},
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): SwingModifier =
    contextMenu(
        onOpen = onOpen,
        onClose = onClose,
        display = ::showPopupAt,
        content = content,
    )

/**
 * Variant of [contextMenu] that lets the caller decide how the populated [JPopupMenu] is presented,
 * instead of the default of showing it over the invoker at the trigger point.
 *
 * @param display invoked with the populated popup, the invoker component, and the trigger point
 *   (x, y in the invoker's coordinates) to present the popup.
 * @param onOpen invoked when the menu has been put on screen.
 * @param onClose invoked when the user closes the menu.
 * @param content the composable menu tree shown in the popup.
 */
@InternalSwingUiApi
@Composable
public fun SwingModifier.contextMenu(
    display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit,
    onOpen: () -> Unit = {},
    onClose: () -> Unit = {},
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): SwingModifier {
    val parentContext = rememberCompositionContext()
    return this then ContextMenuElement(parentContext, onOpen, onClose, display, content)
}

/**
 * Backs every context menu declaration.
 *
 * Two elements are equal only when they declare the same composition and hold the *same* lambdas -
 * identity, because a lambda is what it captures, and a menu built from a fresh one is a different
 * menu.
 */
private class ContextMenuElement(
    private val parentContext: CompositionContext,
    private val onOpen: () -> Unit,
    private val onClose: () -> Unit,
    private val display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit,
    private val content:
        @Composable @SwingMenuComposable
        () -> Unit,
) : SwingModifier.NodeElement<JComponent, ContextMenuElement.Node>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node(parentContext)

    override fun update(node: Node) {
        node.parentContext = parentContext
        node.onOpen = onOpen
        node.onClose = onClose
        node.display = display
        node.content = content
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContextMenuElement) return false
        if (parentContext !== other.parentContext) return false
        if (onOpen !== other.onOpen) return false
        if (onClose !== other.onClose) return false
        if (display !== other.display) return false
        return content === other.content
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(parentContext)
        result = 31 * result + System.identityHashCode(onOpen)
        result = 31 * result + System.identityHashCode(onClose)
        result = 31 * result + System.identityHashCode(display)
        result = 31 * result + System.identityHashCode(content)
        return result
    }

    /**
     * The node backing [ContextMenuElement]: makes the target's popup menu the one every gesture opens,
     * and installs the popup-trigger mouse listener.
     */
    class Node(
        var parentContext: CompositionContext,
    ) : SwingModifier.Node<JComponent>() {
        var onOpen: () -> Unit = {}
        var onClose: () -> Unit = {}
        var display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit = ::showPopupAt
        var content:
            @Composable @SwingMenuComposable
            () -> Unit = {}

        // The target's popup menu: the one menu every gesture reaches, the pointer gesture through the
        // listener below and the keyboard gesture through the binding the look and feel gives it. It
        // stands for the menu across gestures; the menu a gesture puts on screen is a composition of its
        // own, released when that menu closes. Every gesture opens a menu here, which is what makes one
        // report of an open and of a close cover all of them.
        private val popup =
            object : JPopupMenu() {
                override fun show(
                    invoker: Component,
                    x: Int,
                    y: Int,
                ) {
                    val menu = MenuPopup(parentContext, content, onClosed = { onClose() })
                    // The open is reported once the presentation has put the menu on screen, so a
                    // presentation that refuses the menu reports no open.
                    display(menu.popup, invoker, x, y)
                    onOpen()
                }
            }

        private var original: JPopupMenu? = null

        private val listener =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent): Unit = maybeShow(e)

                override fun mouseReleased(e: MouseEvent): Unit = maybeShow(e)

                private fun maybeShow(e: MouseEvent) {
                    // isPopupTrigger is set on press on some platforms and on release on others;
                    // checking both events is the cross-platform-correct gesture detection.
                    if (!e.isPopupTrigger) return
                    // A look and feel that opens a component's popup menu on the gesture itself consumes
                    // the event; this listener performs the gesture for one that does not, and one menu
                    // opens either way.
                    if (e.isConsumed) return
                    popup.show(component, e.x, e.y)
                }
            }

        override fun onAttach() {
            // getComponentPopupMenu() resolves an inherited ancestor menu when the component has none of
            // its own; capturing that would pin the ancestor's menu onto this component as its own on
            // detach, instead of restoring "no own menu".
            original = if (component.inheritsPopupMenu) null else component.componentPopupMenu
            component.componentPopupMenu = popup
            component.addMouseListener(listener)
        }

        override fun onDetach() {
            component.removeMouseListener(listener)
            component.componentPopupMenu = original
        }
    }
}
