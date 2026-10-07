package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTextArea

/** Text a wrapping text area breaks into several lines at a width of a few hundred pixels. */
public const val WRAPPING_TEXT =
    "Wrapping text that is much longer than one line at three hundred and twenty pixels, so it has to break " +
        "into several lines to fit the width the parent gives it, and its height depends on that width."

/** Stock components whose height follows their width, each showing [WRAPPING_TEXT] under the modifier given. */
public val WrappingTexts: Map<String, @Composable (SwingModifier) -> Unit> =
    mapOf(
        "TextArea" to { modifier ->
            TextArea(WRAPPING_TEXT, {}, modifier, lineWrap = true, wrapStyleWord = true)
        },
        "HTML Label" to { modifier -> Label("<html>$WRAPPING_TEXT</html>", modifier = modifier) },
        "HTML EditorPane" to { modifier ->
            EditorPane("<html>$WRAPPING_TEXT</html>", {}, modifier, contentType = "text/html")
        },
    )

/**
 * A text area wrapping [text] at word boundaries that answers its intrinsic sizes as androidx's text does: its height
 * at a width is its text wrapped at that width, so at an unbounded width it is one line per paragraph. It reads them
 * from text areas of its own that are never shown, so asking leaves it as it was.
 */
private class ConstrainableTextArea(
    text: String,
) : JTextArea(text),
    Constrainable {
    /** Lays the text out without wrapping: as wide as its longest line. */
    private val lines = JTextArea(document)

    /** Lays each word out on a line of its own: as wide as its longest word. */
    private val words = JTextArea(text.replace(' ', '\n'))

    /** Wraps the text at the width it is sized to. */
    private val wrapped =
        JTextArea(document).apply {
            lineWrap = true
            wrapStyleWord = true
        }

    init {
        lineWrap = true
        wrapStyleWord = true
    }

    override var constrainedWidth: Int = 0
        private set

    override var constrainedHeight: Int = 0
        private set

    override fun measure(constraints: Constraints) {
        constrainedWidth = constraints.constrainWidth(maxIntrinsicWidth(Constraints.Infinity))
        constrainedHeight = constraints.constrainHeight(maxIntrinsicHeight(constrainedWidth))
    }

    override fun minIntrinsicWidth(height: Int): Int = words.preferredSize.width

    override fun maxIntrinsicWidth(height: Int): Int = lines.preferredSize.width

    override fun minIntrinsicHeight(width: Int): Int = maxIntrinsicHeight(width)

    override fun maxIntrinsicHeight(width: Int): Int {
        // A text area sized narrower than its insets keeps the layout of the width it had, so it is never sized
        // narrower than its longest word.
        val laidOutAt =
            if (width == Constraints.Infinity) Int.MAX_VALUE else maxOf(width, minIntrinsicWidth(Constraints.Infinity))
        wrapped.setSize(laidOutAt, Int.MAX_VALUE)
        return wrapped.preferredSize.height
    }
}

/** A [ConstrainableTextArea] showing [WRAPPING_TEXT] under the modifier given. */
internal val ConstrainableTextAreaLeaf: Map<String, @Composable (SwingModifier) -> Unit> =
    mapOf(
        "ConstrainableTextArea" to { modifier ->
            SwingNode(factory = { ConstrainableTextArea(WRAPPING_TEXT) }, modifier = modifier)
        },
    )
