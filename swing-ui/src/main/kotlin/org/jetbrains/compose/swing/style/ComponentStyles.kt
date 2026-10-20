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
 * Styles provided for every component whose kind applies this key. The library's components apply it before the
 * key of their kind, so a style provided for the kind wins over this one whatever the distance between the two
 * providers. A composable styled with `SwingModifier.styleable { ... }` and no `SwingStyleKind` is not reached by
 * this key.
 *
 * Typed on [ComponentStyleScope], because it also reaches a component that is not a `JComponent`.
 */
public val AllComponents: NestedStyleKey<ComponentStyleScope> = NestedStyleKey("AllComponents")
