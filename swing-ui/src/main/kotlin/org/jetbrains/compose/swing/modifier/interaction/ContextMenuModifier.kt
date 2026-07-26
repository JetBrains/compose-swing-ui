@file:JvmMultifileClass
@file:JvmName("InteractionModifiersKt")

package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.core.MenuApplier
import org.jetbrains.compose.swing.core.SwingCompositionMount
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * Attaches a context menu to the target component, shown when the user requests a popup over it
 * (right-click on most platforms, the platform popup gesture elsewhere).
 *
 * The [content] is the same menu tree used by a menu bar — `Menu`, `MenuItem`, `CheckBoxMenuItem`,
 * `RadioButtonMenuItem`, `MenuSeparator`. Each time the user triggers the popup, [content] is composed
 * fresh into a [JPopupMenu] shown at the cursor; selecting an item runs that item's callback. The menu
 * reads composition state, so the items it shows reflect the current state at the moment the popup
 * opens, and an item's callback updates state like any other composable callback.
 *
 * The menu shares the surrounding composition's recomposition scope and
 * [androidx.compose.runtime.CompositionLocal]s, so state hoisted around the modified component is
 * visible to the menu items and to their callbacks.
 *
 * Call this `@Composable` builder where you build the component's modifier chain, and pass a fresh
 * [content] lambda each recomposition. The popup is dismissed and its resources released when the user
 * closes it.
 *
 * @param content the composable menu tree shown in the popup.
 */
@Composable
public fun SwingModifier.contextMenu(
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): SwingModifier = contextMenu(display = ::showPopupAtCursor, content = content)

/**
 * Variant of [contextMenu] that lets the caller decide how the populated [JPopupMenu] is presented,
 * instead of the default of showing it over the invoker at the trigger point.
 *
 * @param display invoked with the populated popup, the invoker component, and the trigger point
 *   (x, y in the invoker's coordinates) to present the popup.
 * @param content the composable menu tree shown in the popup.
 */
@InternalSwingUiApi
@Composable
public fun SwingModifier.contextMenu(
    display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): SwingModifier {
    val parentContext = rememberCompositionContext()
    return this then ContextMenuElement(parentContext, display, content)
}

/** Presents [popup] over [invoker] at the trigger point — the production default for [contextMenu]. */
private fun showPopupAtCursor(
    popup: JPopupMenu,
    invoker: Component,
    x: Int,
    y: Int,
): Unit = popup.show(invoker, x, y)

/**
 * The additive [SwingModifier.Element] backing [contextMenu]. Installs one popup-trigger mouse
 * listener and, on each trigger, composes the live [content] into a fresh [JPopupMenu], then hands it
 * to [display]. [content] and [display] are read from the node's fields, refreshed by `update`, so a
 * fresh lambda each recomposition is fine and the menu always reflects current state.
 */
@InternalSwingUiApi
public class ContextMenuElement
    @PublishedApi
    internal constructor(
        private val parentContext: CompositionContext,
        private val display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit,
        private val content:
            @Composable @SwingMenuComposable
            () -> Unit,
    ) : SwingModifier.Element<Component, ContextMenuElement.Node> {
        override val targetType: Class<Component> get() = Component::class.java
        override val additive: Boolean get() = true

        override fun create(): Node = Node(parentContext)

        override fun update(node: Node) {
            node.display = display
            node.content = content
        }

        /**
         * The node backing [ContextMenuElement]: installs one popup-trigger mouse listener on attach
         * and, on each trigger, composes the live [content] into a fresh [JPopupMenu] handed to
         * [display].
         */
        @InternalSwingUiApi
        public class Node
            internal constructor(
                private val parentContext: CompositionContext,
            ) : SwingModifier.Node<Component>() {
                internal var display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit =
                    ::showPopupAtCursor
                internal var content:
                    @Composable @SwingMenuComposable
                    () -> Unit = {}

                private val listener =
                    object : MouseAdapter() {
                        override fun mousePressed(e: MouseEvent): Unit = maybeShow(e)

                        override fun mouseReleased(e: MouseEvent): Unit = maybeShow(e)

                        private fun maybeShow(e: MouseEvent) {
                            // isPopupTrigger is set on press on some platforms and on release on others;
                            // checking both events is the cross-platform-correct gesture detection.
                            if (!e.isPopupTrigger) return
                            show(component, e.x, e.y)
                        }
                    }

                override fun onAttach(): Unit = component.addMouseListener(listener)

                override fun onDetach(): Unit = component.removeMouseListener(listener)

                private fun show(
                    invoker: Component,
                    x: Int,
                    y: Int,
                ) {
                    val popup = JPopupMenu()
                    val mount =
                        SwingCompositionMount.nestedUnobserved(parentContext) { MenuApplier(popup) }
                    mount.setContent(content)

                    // Dispose the menu composition when the popup closes, so a recomposed/recycled
                    // invocation never retains a stale menu composition or its listeners.
                    popup.addPopupMenuListener(
                        object : PopupMenuListener {
                            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) = Unit

                            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                                popup.removePopupMenuListener(this)
                                mount.dispose()
                            }

                            override fun popupMenuCanceled(e: PopupMenuEvent) = Unit
                        },
                    )

                    display(popup, invoker, x, y)
                }
            }
    }
