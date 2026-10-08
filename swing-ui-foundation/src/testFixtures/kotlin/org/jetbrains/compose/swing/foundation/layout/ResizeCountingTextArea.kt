package org.jetbrains.compose.swing.foundation.layout

import javax.swing.JTextArea

/** A wrapping text area counting the times its width and its size change. */
public class ResizeCountingTextArea(
    text: String = WRAPPING_TEXT,
) : JTextArea(text) {
    /** The times [setBounds] gave the area another width. */
    public var widthChanges: Int = 0
        private set

    /** The times [setBounds] gave the area another size. */
    public var resizes: Int = 0
        private set

    init {
        lineWrap = true
        wrapStyleWord = true
    }

    /** Starts both counts again from zero. */
    public fun resetCounts() {
        widthChanges = 0
        resizes = 0
    }

    override fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (width != this.width) widthChanges++
        if (width != this.width || height != this.height) resizes++
        super.setBounds(x, y, width, height)
    }
}
