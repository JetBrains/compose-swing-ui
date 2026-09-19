package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Recomposer
import org.jetbrains.compose.swing.util.get
import java.awt.Component
import java.awt.Window
import javax.swing.JComponent

/**
 * The [Recomposer] driving this component's composed content, or `null` where nothing this library
 * mounted drives it.
 *
 * The search starts at this component and walks up its Swing ancestors as far as the window holding
 * it, so a container carrying a content composition answers for that composition, and anything nested
 * inside one answers for the scope around it. A [Window] with a root pane answers for the content
 * standing in it, whether that content composes on a recomposer the window owns or under a context a
 * caller named - which is what a window declared inside `application { }` composes under. A window owned
 * by another answers for itself rather than for its owner. It reads what is already there and starts
 * nothing.
 *
 * `null` covers a component nothing above it answers for: one no composed content stands in, one whose
 * content composes under a recomposer that is not this library's, and a window with no root pane, which
 * shares no recomposer to answer with - its content still answers, asked where it stands.
 *
 * Must be called on the Event Dispatch Thread.
 */
public fun Component.findRecomposer(): Recomposer? {
    checkEventDispatchThread()
    // An owned window's Swing parent is the window that owns it, and no composition reaches across that
    // link: content in an owned window composes on that window's own recomposer, or on a context its
    // caller named, which the walk meets before the window itself. So the walk ends at the first window.
    return generateSequence(this) { if (it is Window) null else it.parent }
        .firstNotNullOfOrNull { it.recomposerOrNull() }
}

/**
 * The [Recomposer] directly associated with this component, reading nothing above it: a host's published context,
 * a content composition running on it, or a window's shared recomposer.
 */
private fun Component.recomposerOrNull(): Recomposer? =
    when (this) {
        is JComponent -> {
            get(COMPOSITION_KEY)?.drivingRecomposer()
                ?: contentCompositionOrNull()?.publishedContext?.drivingRecomposer()
        }

        is Window -> {
            swingRecomposerOrNull()?.recomposer
                ?: contentPaneOrNull?.recomposerOrNull()
        }

        else -> {
            null
        }
    }
