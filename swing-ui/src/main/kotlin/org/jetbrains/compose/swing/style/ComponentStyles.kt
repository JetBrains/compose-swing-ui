@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

/**
 * The properties of a `JComponent`. A third-party `JComponent` builds its
 * own scope on this one, or on a narrower one, so every setter declared here works in its style too.
 */
public interface SwingComponentStyleScope : ComponentStyleScope

/**
 * What a style can do on a container: set Component and JComponent properties and provide nested styles to the
 * components below it. `SwingModifier.styleable { ... }` without a `SwingStyleKind` gives this scope, so it is the
 * way to style a composable that does not take a `style` parameter.
 */
public interface ContainerStyleScope :
    SwingComponentStyleScope,
    NestedStyleProviderScope

/**
 * Styles for every descendant in the same composition, including descendants without a style declaration.
 * A style provided for the descendant's kind takes precedence, regardless of how far away each provider is.
 *
 * Typed on [ComponentStyleScope], because it also reaches a component that is not a `JComponent`.
 */
public val AllComponents: NestedStyleKey<ComponentStyleScope> = NestedStyleKey("AllComponents")
