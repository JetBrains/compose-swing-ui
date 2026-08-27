package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Recomposer
import java.awt.Component
import java.awt.Window
import javax.swing.JComponent

/**
 * The [Recomposer] driving this component's composed content, or `null` where nothing this library
 * mounted drives it.
 *
 * The search starts at this component and walks up its Swing ancestors as far as the window holding
 * it, so a container carrying an island answers for that island, and anything nested inside one
 * answers for the scope around it. A [Window] answers with the runtime it owns, and a window owned by
 * another answers for itself rather than for its owner. It reads what is already there and starts
 * nothing.
 *
 * `null` covers a component nothing above it answers for: one no composed content stands in, and one
 * whose content composes under a context a caller captured with `rememberCompositionContext()`. A
 * window declared inside `application { }` is the latter: its content is part of the application's
 * composition, whose recomposer is [org.jetbrains.compose.swing.window.ApplicationScope.recomposer].
 *
 * Must be called on the Event Dispatch Thread.
 */
public fun Component.findRecomposer(): Recomposer? {
    checkEventDispatchThread()
    // An owned window's Swing parent is the window that owns it, and no composition reaches across that
    // link: content in an owned window composes on that window's own runtime, or on a context its caller
    // named, which the walk meets before the window itself. So the walk ends at the first window.
    return generateSequence(this) { if (it is Window) null else it.parent }
        .firstNotNullOfOrNull { it.publishedRecomposer() }
}

/**
 * The [Recomposer] published on this component itself, reading nothing above it: the context stamped on
 * it, the runtime a window holds, or the context an island composing into it runs under.
 *
 * A window is asked for the runtime it holds rather than for a stamp, because a window publishes its own
 * on its root pane - a child of the window, which a walk towards the ancestors never reaches.
 *
 * A context that is no [Recomposer] was published by a live composition and hides the scope behind it,
 * so the caller passes over it and carries on up.
 */
private fun Component.publishedRecomposer(): Recomposer? =
    (this as? JComponent)?.get(COMPOSITION_KEY) as? Recomposer
        ?: (this as? Window)?.swingRecomposerOrNull()?.recomposer
        ?: islandCompositionContextOrNull() as? Recomposer
