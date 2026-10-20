@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import javax.swing.JComponent

/**
 * Declares [style] as the component's style. The style can set every java.awt.Component and JComponent property
 * and provide nested styles to the components below. Put it last in the modifier.
 *
 * Throws IllegalArgumentException when applied to a component that is not a JComponent; use the overload taking
 * a SwingStyleKind there.
 *
 * A component takes one styleable: a second one in the same modifier throws IllegalArgumentException.
 */
public fun SwingModifier.styleable(style: SwingStyle<ContainerStyleScope>): SwingModifier =
    styleable(style, ContainerKind)

internal val ContainerKind =
    SwingStyleKind<ContainerStyleScope, Unit>(
        scope = { base, _ -> object : ContainerStyleScope, NestedStyleProviderScope by base {} },
        default = SwingStyle,
        defaultOnlyNests = false,
        requiredType = JComponent::class.java,
    )

/** Fails when [kind] requires a component type that [component] is not. */
internal fun requireKindTarget(
    kind: SwingStyleKind<*, *>,
    component: Component,
) {
    val required = kind.requiredType
    require(required.isInstance(component)) {
        "styleable(style) needs a ${required.name}, but the component is a ${component.javaClass.name}; " +
            "use the styleable overload taking a SwingStyleKind."
    }
}

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
