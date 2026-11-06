package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextColorRepaintTest {
    private class CountingTextField : JTextField() {
        var repaintCount: Int = 0
            private set

        override fun repaint() {
            repaintCount++
            super.repaint()
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["caret", "selection", "selectedText", "disabledText"])
    fun colorChangesRepaintAndRestore(property: String) = runComposeSwingTest {
        val field = CountingTextField()
        val read: JTextComponent.() -> Color = {
            when (property) {
                "caret" -> caretColor
                "selection" -> selectionColor
                "selectedText" -> selectedTextColor
                else -> disabledTextColor
            }
        }
        val original = field.read()
        var color by mutableStateOf<Color?>(Color(0x11, 0x22, 0x33))
        setContent {
            SwingNode(
                factory = { field },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        color?.let {
                            when (property) {
                                "caret" -> caretColor(it)
                                "selection" -> selectionColor(it)
                                "selectedText" -> selectedTextColor(it)
                                else -> disabledTextColor(it)
                            }
                        }
                    },
            )
        }
        assertEquals(Color(0x11, 0x22, 0x33), field.read(), property)
        val repaintsBefore = field.repaintCount
        color = Color(0xAA, 0xBB, 0xCC)
        awaitIdle()
        assertEquals(Color(0xAA, 0xBB, 0xCC), field.read(), property)
        assertTrue(field.repaintCount > repaintsBefore, "$property must repaint after a color change")
        color = null
        awaitIdle()
        assertEquals(original, field.read(), "$property restores the installed color")
    }
}
