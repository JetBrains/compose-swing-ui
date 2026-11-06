@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.SwingModifier
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
 * @property defaultOnlyNests whether the default is restricted to applying nested styles.
 * @property requiredType the component type required by this kind.
 */
public class SwingStyleKind<S : ComponentStyleScope, in T> internal constructor(
    public val scope: (base: NestedStyleProviderScope, state: T) -> S,
    public val default: SwingStyle<S>,
    internal val defaultOnlyNests: Boolean,
    internal val requiredType: Class<out Component> = Component::class.java,
) {
    public constructor(
        scope: (base: NestedStyleProviderScope, state: T) -> S,
        default: SwingStyle<S> = SwingStyle,
    ) : this(scope, default, false)

    /** The stateless empty declaration, shared by components of this kind. */
    private var unstyled: SwingModifier.Element? = null

    /**
     * Creates the declaration, reusing the stateless empty declaration when possible.
     *
     * @param style the caller's style.
     * @param state the current scope input.
     */
    internal fun element(
        style: SwingStyle<S>,
        state: T,
    ): SwingModifier.Element {
        if (style !== SwingStyle || state !== Unit) {
            return StyleElement(this, style, state)
        }
        return unstyled ?: StyleElement(this, style, state).also { unstyled = it }
    }
}

/** Whether the default of this kind is not run for a component with no style and no publishing ancestor. */
internal val SwingStyleKind<*, *>.readsNothingByDefault: Boolean get() = default === SwingStyle || defaultOnlyNests

/**
 * A kind whose [default] only applies nested styles, such as `applyNestedStyle(key)`. With no style of its own and no
 * publishing ancestor, a component of this kind runs no default and holds no style resolution.
 *
 * The default may contain nothing else: it fails with an [IllegalStateException] when it runs and writes a property
 * outside an applied nested style, because that write would be lost wherever the default is not run. A kind whose
 * default sets a property itself is built with the [SwingStyleKind] constructor. The caller's own style is not
 * restricted.
 */
public fun <S : ComponentStyleScope, T> nestingStyleKind(
    scope: (base: NestedStyleProviderScope, state: T) -> S,
    default: SwingStyle<S>,
): SwingStyleKind<S, T> = SwingStyleKind(scope, default, defaultOnlyNests = true)
