// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import java.awt.Component

/**
 * How a component kind is styled: how its scope is built and what its default style does. Declare one per
 * component kind, as a top-level `val`. A style of this kind runs against the scope `S`, which the kind builds over
 * the state `T` the component feeds it, [Unit] for a kind without state.
 *
 * @property scope builds the scope a style of this kind runs against, over [base], which implements
 *   [NestedStyleProviderScope]. The scope delegates to [base] the interfaces it extends.
 * @property default the style applied before the caller's own. A component that applies nested styles applies
 *   their keys here.
 * @property requiredType the component type required by this kind.
 */
public class SwingStyleKind<S : ComponentStyleScope, in T> internal constructor(
    public val scope: (base: NestedStyleProviderScope, state: T) -> S,
    public val default: SwingStyle<S>,
    internal val requiredType: Class<out Component>,
    defaultWrites: DefaultWrites = DefaultWrites.AnyProperty,
) {
    /**
     * A kind for components of any [Component] type.
     *
     * @param scope builds the scope a style of this kind runs against, over [base], which implements
     *   [NestedStyleProviderScope]. The scope delegates to [base] the interfaces it extends.
     * @param default the style applied before the caller's own. A component that applies nested styles applies
     *   their keys here.
     */
    public constructor(
        scope: (base: NestedStyleProviderScope, state: T) -> S,
        default: SwingStyle<S> = SwingStyle,
    ) : this(scope, default, Component::class.java)

    /** Whether the default only applies nested styles, so it is skipped with no style and no publishing ancestor. */
    internal val defaultAppliesOnlyNestedStyles: Boolean =
        defaultWrites === DefaultWrites.NestedStylesOnly || default === SwingStyle

    /** The stateless empty declaration, shared by components of this kind. */
    private var unstyled: StyleElement<S, *>? = null

    /**
     * Creates the declaration, reusing the stateless empty declaration when possible.
     *
     * @param style the caller's style.
     * @param state the current scope input.
     */
    internal fun element(
        style: SwingStyle<S>,
        state: T,
    ): StyleElement<S, *> {
        if (style !== SwingStyle || state !== Unit) {
            return StyleElement(this, style, state)
        }
        return unstyled ?: StyleElement(this, style, state).also { unstyled = it }
    }
}

/**
 * A kind whose [default] only applies nested styles, such as `applyNestedStyle(key)`. With no style of its own and no
 * publishing ancestor, a component of this kind runs no default and holds no style resolution.
 *
 * The default may contain nothing else: it fails with an [IllegalStateException] when it runs and writes a property
 * outside an applied nested style, because that write would be lost wherever the default is not run. A kind whose
 * default sets a property itself is built with the [SwingStyleKind] constructor. The caller's own style is not
 * restricted.
 *
 * @param scope builds the scope a style of this kind runs against from its base and the kind's state. The scope
 *   delegates to the base the interfaces it extends.
 * @param default the style applied before the caller's own; it may only apply nested styles.
 */
public fun <S : ComponentStyleScope, T> nestingStyleKind(
    scope: (base: NestedStyleProviderScope, state: T) -> S,
    default: SwingStyle<S>,
): SwingStyleKind<S, T> = SwingStyleKind(scope, default, Component::class.java, DefaultWrites.NestedStylesOnly)

/** What the default style of a [SwingStyleKind] may write. */
internal sealed interface DefaultWrites {
    /** The default may write any property. */
    data object AnyProperty : DefaultWrites

    /** The default may only apply nested styles. */
    data object NestedStylesOnly : DefaultWrites
}
