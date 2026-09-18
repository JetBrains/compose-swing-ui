package org.jetbrains.compose.swing.core

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import kotlin.coroutines.CoroutineContext

/**
 * The debug-only checks the compositions on one recomposer are held to, installed as an element of the
 * coroutine context that recomposer was built over.
 *
 * A composition reads it as it is mounted, and one nested under it reads the same element through its
 * parent's effect context - a cell renderer's composition, a menu's, a window's. Nothing installs one in
 * production.
 *
 * Every call runs on the event dispatch thread, from inside the change pass that provoked it. A violation
 * is reported rather than thrown: a throw from inside a modifier write reaches the apply phase and ends
 * the recomposer for good.
 *
 * Marked [InternalSwingUiApi]; it may change or be removed without notice in any release.
 */
@InternalSwingUiApi
public interface SwingCompositionDiagnostics : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    /**
     * Runs [write] - what one element writes onto [component] for the slot [node] holds, whether that is
     * the slot's first write or a later one.
     *
     * [node] identifies the slot for as long as it holds one, which the element does not: a pass hands
     * the slot a fresh element carrying the same declaration. [element] is that declaration, and is what
     * a report about the write names it by.
     */
    public fun declaring(
        component: Component,
        node: SwingModifier.ComponentNode<*>,
        element: SwingModifier.NodeElement<*, *>,
        write: () -> Unit,
    )

    /**
     * Runs [restore] - the departing slot [node] holds putting [component] back where the modifier found it.
     */
    public fun restoring(
        component: Component,
        node: SwingModifier.ComponentNode<*>,
        restore: () -> Unit,
    )

    /** The key this element is read off a coroutine context by. */
    @InternalSwingUiApi
    public companion object Key : CoroutineContext.Key<SwingCompositionDiagnostics>
}

/**
 * Runs [write] - what one modifier element writes onto [component] - under the checks the composition is
 * held to, or plainly where it is held to none.
 *
 * A composition outside a test names no diagnostics, and takes the `null` branch, which inlines the write
 * where it stands and builds no lambda for it.
 */
internal inline fun SwingCompositionDiagnostics?.watchWrite(
    component: Component,
    node: SwingModifier.ComponentNode<*>,
    element: SwingModifier.NodeElement<*, *>,
    crossinline write: () -> Unit,
) {
    if (this == null) write() else declaring(component, node, element) { write() }
}

/** [watchWrite], for the departing slot putting [component] back where the modifier found it. */
internal inline fun SwingCompositionDiagnostics?.watchRestore(
    component: Component,
    node: SwingModifier.ComponentNode<*>,
    crossinline restore: () -> Unit,
) {
    if (this == null) restore() else restoring(component, node) { restore() }
}
