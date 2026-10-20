@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Applies [kind]'s default style followed by [style] to this component.
 *
 * The style runs during component application and when an observed input changes.
 * Put this declaration after modifiers whose properties the style should override.
 * Multiple styles apply in modifier order.
 *
 * The styleable API is subject to change while the AndroidX Styles API it follows
 * is experimental.
 *
 * @param kind builds the style scope and supplies the default style.
 * @param style the caller's style, applied after the kind's default.
 * @return this modifier followed by the style declaration.
 */
public fun <S : ComponentStyleScope> SwingModifier.styleable(
    kind: SwingStyleKind<S, Unit>,
    style: SwingStyle<S>,
): SwingModifier = this then kind.element(style, Unit)

/**
 * Applies [kind]'s default style followed by [style] to this component.
 *
 * The style runs during component application and when an observed input changes.
 * Put this declaration after modifiers whose properties the style should override.
 * Multiple styles apply in modifier order.
 *
 * The styleable API is subject to change while the AndroidX Styles API it follows
 * is experimental.
 *
 * @param kind builds the style scope and supplies the default style.
 * @param state the current input used to build the scope; a different instance rebuilds it.
 * @param style the caller's style, applied after the kind's default.
 * @return this modifier followed by the style declaration.
 */
public fun <S : ComponentStyleScope, T> SwingModifier.styleable(
    kind: SwingStyleKind<S, T>,
    state: T,
    style: SwingStyle<S>,
): SwingModifier = this then kind.element(style, state)
