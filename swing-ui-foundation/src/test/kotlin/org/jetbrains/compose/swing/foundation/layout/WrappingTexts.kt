package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier

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
