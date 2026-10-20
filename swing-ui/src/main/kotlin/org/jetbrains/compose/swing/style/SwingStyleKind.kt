package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * How a component kind is styled: how its scope is built and what its default style does. Declare one per
 * component kind, as a top-level `val`. A style of this kind runs against the scope `S`, which the kind builds over
 * the state `T` the component feeds it, [Unit] for a kind without state.
 *
 * @property scope builds the scope a style of this kind runs against, over [base], which implements
 *   [NestedStyleProviderScope]. The scope delegates to [base] the interfaces it extends.
 * @property default the style applied before the caller's own. A component that applies nested styles applies
 *   their keys here.
 */
public class SwingStyleKind<S : ComponentStyleScope, in T>(
    public val scope: (base: NestedStyleProviderScope, state: T) -> S,
    public val default: SwingStyle<S> = SwingStyle,
) {
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
