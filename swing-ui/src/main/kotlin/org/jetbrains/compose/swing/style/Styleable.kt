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
 * Multiple styles apply in modifier order.
 *
 * The styleable API is subject to change while the AndroidX Styles API it follows
 * is experimental.
 *
 * @param style the caller's style.
 * @return this modifier followed by the style declaration.
 */
public fun SwingModifier.styleable(style: SwingStyle<ContainerStyleScope>): SwingModifier =
    styleable(ContainerKind, style)

/**
 * The declaration of a component that declares no style of its own, so a style provided for [AllComponents] reaches
 * it. It holds a slot only while an ancestor provides nested styles.
 */
internal val UnstyledComponent: StyleElement<*, *> =
    nestingStyleKind<ComponentStyleScope, Unit>({ base, _ -> object : ComponentStyleScope by base {} }, SwingStyle)
        .element(SwingStyle, Unit)

internal val ContainerKind =
    SwingStyleKind<ContainerStyleScope, Unit>(
        scope = { base, _ -> object : ContainerStyleScope, NestedStyleProviderScope by base {} },
        default = SwingStyle,
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
