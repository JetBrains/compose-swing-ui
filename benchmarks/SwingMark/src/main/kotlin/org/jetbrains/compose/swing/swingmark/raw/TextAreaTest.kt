package org.jetbrains.compose.swing.swingmark.raw

import org.jetbrains.compose.swing.swingmark.fixtures.TEXT_AREA_DISPLAY_STRING
import org.jetbrains.compose.swing.swingmark.harness.rest
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

/** `TextAreaTest`: text appended to a wrapping area, with a line break every twelfth append. */
internal class TextAreaTest(
    private val blitScrolling: Boolean,
) : RawTest() {
    override val testName: String = "TextArea"

    private lateinit var textArea1: JTextArea

    override fun testComponent(): JComponent {
        val panel = JPanel()
        textArea1 = CountTextArea(ROWS, COLUMNS)
        textArea1.lineWrap = true
        val scroller = JScrollPane(textArea1)
        if (blitScrolling) {
            scroller.viewport.putClientProperty(ENABLE_WINDOW_BLIT, true)
        }
        panel.add(scroller)
        return panel
    }

    override fun runTest() {
        testTextArea(textArea1, TEXT_AREA_DISPLAY_STRING)
    }

    private fun testTextArea(
        currentTextArea: JTextArea,
        appendThis: String,
    ) {
        val appender = TextAppender(currentTextArea, appendThis)
        for (i in 0 until REPEAT) {
            appender.appendString = appendThis
            if (i % BREAK_INCREMENT == BREAK_INCREMENT - 1) {
                appender.appendString = appendThis + "\n"
            }
            post(appender)
            rest()
        }
    }

    private inner class CountTextArea(
        rows: Int,
        columns: Int,
    ) : JTextArea(rows, columns) {
        override fun paint(g: Graphics) {
            super.paint(g)
            paintCount++
        }
    }

    private companion object {
        const val REPEAT = 300
        const val BREAK_INCREMENT = 12
        const val ROWS = 10
        const val COLUMNS = 30
    }
}

/** Appends whatever it is holding, which the test sets before each of its passes. */
private class TextAppender(
    private val area: JTextArea,
    var appendString: String,
) : Runnable {
    override fun run() {
        area.append(appendString)
    }
}
