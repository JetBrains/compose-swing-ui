package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test

class SizeStyleTest {
    private fun ComposeSwingTest.sizedChild(style: SwingStyle<LabelStyleScope>): SwingNodeInteraction<JLabel> {
        setContent {
            LayeredPane {
                Label("child", SwingModifier.layer(JLayeredPane.DEFAULT_LAYER), style = style)
            }
        }
        return onNodeOfType<JLabel>()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sizeCases")
    fun sizeDeclarationsRespectOrder(
        name: String,
        style: SwingStyle<LabelStyleScope>,
        expected: Dimension,
    ) = runComposeSwingTest {
        sizedChild(style).assertProperty(expected, name) { size }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun wholeSizeCanJoinAndLeaveAnAxis(initiallyWhole: Boolean) = runComposeSwingTest {
        var whole by mutableStateOf(initiallyWhole)
        val child =
            sizedChild {
                if (whole) size(120, 40)
                width(10)
            }
        if (!initiallyWhole) {
            child.assertProperty(10) { width }
            whole = true
            awaitIdle()
            child.assertProperty(10) { width }
            child.assertProperty(40) { height }
        }
        child.assertProperty(Dimension(10, 40)) { size }
        whole = false
        awaitIdle()
        child.assertProperty(Dimension(10, 0)) { size }
    }

    @Test
    fun wholeSizeAndAxisCanBothLeave() = runComposeSwingTest {
        var declared by mutableStateOf(0)
        val child =
            sizedChild {
                if (declared == 1) size(120, 40)
                if (declared in 1..2) width(10)
            }
        declared = 1
        awaitIdle()
        child.assertProperty(Dimension(10, 40)) { size }
        declared = 2
        awaitIdle()
        child.assertProperty(Dimension(10, 0)) { size }
        declared = 3
        awaitIdle()
        child.assertProperty(Dimension(0, 0)) { size }
    }

    @Test
    fun reorderingUnchangedDeclarationsChangesTheWinningAxis() = runComposeSwingTest {
        var sizeFirst by mutableStateOf(true)
        val child =
            sizedChild {
                if (sizeFirst) {
                    size(20, 30)
                    width(10)
                    foreground(Color.RED)
                    background(Color.BLUE)
                } else {
                    width(10)
                    size(20, 30)
                    background(Color.BLUE)
                    foreground(Color.RED)
                }
            }
        child.assertProperty(Dimension(10, 30)) { size }
        sizeFirst = false
        awaitIdle()
        child.assertProperty(Dimension(20, 30)) { size }
    }

    companion object {
        @JvmStatic
        fun sizeCases(): List<Arguments> = listOf(
            Arguments.of(
                "width then size",
                SwingStyle<LabelStyleScope> {
                    width(10)
                    size(20, 30)
                },
                Dimension(20, 30),
            ),
            Arguments.of(
                "width keeps height",
                SwingStyle<LabelStyleScope> {
                    size(50, 33)
                    width(150)
                },
                Dimension(150, 33),
            ),
            Arguments.of(
                "height keeps width",
                SwingStyle<LabelStyleScope> {
                    size(44, 50)
                    height(55)
                },
                Dimension(44, 55),
            ),
            Arguments.of(
                "width then height",
                SwingStyle<LabelStyleScope> {
                    width(10)
                    height(20)
                },
                Dimension(10, 20),
            ),
            Arguments.of(
                "height then width",
                SwingStyle<LabelStyleScope> {
                    height(20)
                    width(10)
                },
                Dimension(10, 20),
            ),
            Arguments.of(
                "last width wins",
                SwingStyle<LabelStyleScope> {
                    width(10)
                    size(20, 30)
                    width(5)
                },
                Dimension(5, 30),
            ),
        )
    }
}
