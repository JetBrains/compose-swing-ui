@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Declares [decorator] on a component that paints through a decoration: a component implementing
 * [Decoratable]. Any other component is refused, with an IllegalStateException naming `Decoratable`.
 *
 * Every decorator a chain declares is kept, in declaration order, rather than the last one winning:
 * `SwingModifier.decoration(a).decoration(b)` paints `a` around `b`. A declaration whose decorator is equal
 * across a recomposition leaves the component's painting alone.
 *
 * [decorator] paints at the component's layout bounds.
 *
 * @param decorator what wraps the component's painting inside everything declared before it.
 * @return this chain with the decorator declared on it.
 */
public fun SwingModifier.decoration(decorator: Decorator): SwingModifier = decoration(DecoratorElement(decorator))

/**
 * Declares [element], whose [DecorationModifierNode] is one step of the component's decoration, such as a
 * [DrawModifierNode]. It paints at its place among the other steps, as a declared [Decorator] does, so the
 * component must be a [Decoratable] and [element] must be [additive][SwingModifier.NodeElement.additive];
 * either is refused, before the node attaches when [element] names [Decoratable] as its target type, or as
 * the node attaches otherwise.
 *
 * @param element the step to declare, creating the node that paints it.
 * @return this chain with the step declared on it.
 */
public fun SwingModifier.decoration(
    element: SwingModifier.NodeElement<*, out DecorationModifierNode<*>>,
): SwingModifier = this then element
