package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.setterCaseTests
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JTextField
import javax.swing.text.JTextComponent

class TextColorRepaintTest {
    @TestFactory
    fun settersWriteUpdateAndRestore(): List<DynamicTest> = setterCaseTests(
        listOf(
            textCase("caretColor", Color.RED, Color.BLUE, { it.caretColor }) { caretColor(it) },
            textCase("selectionColor", Color.RED, Color.BLUE, { it.selectionColor }) { selectionColor(it) },
            textCase("selectedTextColor", Color.RED, Color.BLUE, { it.selectedTextColor }) { selectedTextColor(it) },
            textCase("disabledTextColor", Color.RED, Color.BLUE, { it.disabledTextColor }) { disabledTextColor(it) },
        ),
    )

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
        val read: JTextComponent.() -> Color = {
            when (property) {
                "caret" -> caretColor
                "selection" -> selectionColor
                "selectedText" -> selectedTextColor
                else -> disabledTextColor
            }
        }
        var color by mutableStateOf<Color?>(Color(0x11, 0x22, 0x33))
        setContent {
            SwingNode(
                factory = { CountingTextField() },
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
        onNodeOfType<CountingTextField>().assertProperty(Color(0x11, 0x22, 0x33), property) { read() }
        var repaintsBefore = 0
        onNodeOfType<CountingTextField>().assertProperty(true) {
            repaintsBefore = repaintCount
            true
        }
        color = Color(0xAA, 0xBB, 0xCC)
        awaitIdle()
        onNodeOfType<CountingTextField>().apply {
            assertProperty(Color(0xAA, 0xBB, 0xCC), property) { read() }
            assertProperty(true, "$property must repaint after a color change") { repaintCount > repaintsBefore }
        }
        color = null
        awaitIdle()
        onNodeOfType<CountingTextField>().assertProperty(
            JTextField().read(),
            "$property restores the installed color",
        ) { read() }
    }
}
