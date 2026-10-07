package org.jetbrains.compose.swing.foundation.layout

import javax.swing.JTextArea

/** A wrapping text area counting the times its size changes. */
public class ResizeCountingTextArea(
    text: String = WRAPPING_TEXT,
) : JTextArea(text) {
    /** The times [setBounds] gave the area another size. */
    public var resizes: Int = 0

    init {
        lineWrap = true
        wrapStyleWord = true
    }

    override fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (width != this.width || height != this.height) resizes++
        super.setBounds(x, y, width, height)
    }
}
