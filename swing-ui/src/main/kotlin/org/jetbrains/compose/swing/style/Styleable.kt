@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Applies [kind]'s default style followed by [style] to this component.
 *
 * The style runs during component application and when an observed input changes.
 * Put this declaration after modifiers whose properties the style should override.
 * A component accepts one styleable declaration.
 *
 * The styleable API is subject to change while the AndroidX Styles API it follows
 * is experimental.
 *
 * @param style the caller's style, applied after the kind's default.
 * @param kind builds the style scope and supplies the default style.
 * @return this modifier followed by the style declaration.
 */
public fun <S : ComponentStyleScope> SwingModifier.styleable(
    style: SwingStyle<S>,
    kind: SwingStyleKind<S, Unit>,
): SwingModifier = this then kind.element(style, Unit)

/**
 * Applies [kind]'s default style followed by [style] to this component.
 *
 * The style runs during component application and when an observed input changes.
 * Put this declaration after modifiers whose properties the style should override.
 * A component accepts one styleable declaration.
 *
 * The styleable API is subject to change while the AndroidX Styles API it follows
 * is experimental.
 *
 * @param style the caller's style, applied after the kind's default.
 * @param kind builds the style scope and supplies the default style.
 * @param state the current input used to build the scope; a different instance rebuilds it.
 * @return this modifier followed by the style declaration.
 */
public fun <S : ComponentStyleScope, T> SwingModifier.styleable(
    style: SwingStyle<S>,
    kind: SwingStyleKind<S, T>,
    state: T,
): SwingModifier = this then kind.element(style, state)
