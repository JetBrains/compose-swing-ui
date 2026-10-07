package org.jetbrains.compose.swing.foundation.layout

import javax.swing.JComponent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Asserts [text] holds the height it prefers at the width it holds, and that this is several lines. */
public fun assertWrapped(
    text: JComponent,
    pass: String,
) {
    val inner = text.height - text.insets.top - text.insets.bottom
    assertTrue(
        inner > text.getFontMetrics(text.font).height,
        "$pass wraps the text into several lines, but it is ${text.height} tall",
    )
    assertEquals(
        text.preferredSize.height,
        text.height,
        "$pass lays the text out at the height it prefers at its width",
    )
}
