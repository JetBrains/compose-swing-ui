package org.jetbrains.compose.swing.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * A [ParentLayoutElement] backed by a stateful [ParentLayoutNode] rather than a plain value, mirroring
 * `androidx.compose.ui.node.ModifierNodeElement`.
 *
 * [create] runs once, the first time this declaration's slot is filled. [update] runs whenever a later
 * declaration replaces one this element's own [equals] finds unequal, with the node already attached.
 */
public abstract class ParentLayoutNodeElement<N : ParentLayoutNode> : ParentLayoutElement {
    /** Builds the node this declaration's slot holds for as long as an element of this type fills it. */
    public abstract fun create(): N

    /** Brings [node] up to date with this declaration. */
    public abstract fun update(node: N)

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int
}

/**
 * The stateful side of a [ParentLayoutNodeElement]: kept for as long as an element of its slot's type
 * stands, carrying state a plain declaration cannot.
 *
 * Implements [ParentLayoutElement] itself, because a diffed slot's node - not the throwaway element that
 * last updated it - is what a measuring parent reads from then on. A concrete subclass declares its
 * [parentProtocol], the same one its element declares.
 */
public abstract class ParentLayoutNode :
    SwingModifier.Node(),
    ParentLayoutElement
