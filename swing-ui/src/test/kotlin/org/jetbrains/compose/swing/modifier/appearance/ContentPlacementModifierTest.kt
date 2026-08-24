package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Where a component's content sits, and where its text sits relative to its icon, are properties of
 * the component rather than of one wrapper, so they are carried by modifiers that reach every
 * component declaring them. Each is declared separately by a label, by anything built on a button,
 * and - for horizontal alignment - by a text field.
 */
class ContentPlacementModifierTest {
    @Test
    fun horizontalAlignmentReachesEachKindThatDeclaresIt() = runComposeSwingTest {
        setContent {
            Label("Legend", modifier = SwingModifier.horizontalAlignment(SwingConstants.RIGHT))
            Button("Save", onClick = { }, modifier = SwingModifier.horizontalAlignment(SwingConstants.LEFT))
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.horizontalAlignment(SwingConstants.CENTER),
            )
        }

        assertEquals(SwingConstants.RIGHT, onNodeOfType<JLabel>().fetch().horizontalAlignment, "label")
        assertEquals(SwingConstants.LEFT, onNodeOfType<JButton>().fetch().horizontalAlignment, "button")
        assertEquals(SwingConstants.CENTER, onNodeOfType<JTextField>().fetch().horizontalAlignment, "field")
    }

    @Test
    fun verticalAlignmentReachesLabelsAndButtons() = runComposeSwingTest {
        setContent {
            Label("Legend", modifier = SwingModifier.verticalAlignment(SwingConstants.TOP))
            Button("Save", onClick = { }, modifier = SwingModifier.verticalAlignment(SwingConstants.BOTTOM))
        }

        assertEquals(SwingConstants.TOP, onNodeOfType<JLabel>().fetch().verticalAlignment, "label")
        assertEquals(SwingConstants.BOTTOM, onNodeOfType<JButton>().fetch().verticalAlignment, "button")
    }

    @Test
    fun aTextFieldHasNoVerticalAlignmentToSet() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        TextField(
                            value = "",
                            onValueChange = {},
                            modifier = SwingModifier.verticalAlignment(SwingConstants.TOP),
                        )
                    }
                }
            }

        assertTrue(
            "verticalAlignment" in failure.message.orEmpty(),
            "the message should name the property: ${failure.message}",
        )
    }

    @Test
    fun textPositionAndGapReachALabel() = runComposeSwingTest {
        setContent {
            Label(
                "Legend",
                modifier =
                    SwingModifier
                        .horizontalTextPosition(SwingConstants.LEADING)
                        .verticalTextPosition(SwingConstants.BOTTOM)
                        .iconTextGap(GAP),
            )
        }

        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(SwingConstants.LEADING, label.horizontalTextPosition, "horizontalTextPosition")
        assertEquals(SwingConstants.BOTTOM, label.verticalTextPosition, "verticalTextPosition")
        assertEquals(GAP, label.iconTextGap, "iconTextGap")
    }

    @Test
    fun textPositionAndGapReachAButton() = runComposeSwingTest {
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier =
                    SwingModifier
                        .horizontalTextPosition(SwingConstants.LEADING)
                        .verticalTextPosition(SwingConstants.TOP)
                        .iconTextGap(GAP),
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertEquals(SwingConstants.LEADING, button.horizontalTextPosition, "horizontalTextPosition")
        assertEquals(SwingConstants.TOP, button.verticalTextPosition, "verticalTextPosition")
        assertEquals(GAP, button.iconTextGap, "iconTextGap")
    }

    @Test
    fun alignmentFollowsTheStateDrivingIt() = runComposeSwingTest {
        var alignment by mutableIntStateOf(SwingConstants.LEFT)
        setContent { Label("Legend", modifier = SwingModifier.horizontalAlignment(alignment)) }

        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(SwingConstants.LEFT, label.horizontalAlignment, "the declared alignment")

        alignment = SwingConstants.RIGHT
        awaitIdle()

        assertEquals(SwingConstants.RIGHT, label.horizontalAlignment, "the alignment follows its state")
    }

    @Test
    fun droppingAlignmentRestoresWhatTheComponentHad() = runComposeSwingTest {
        var aligned by mutableIntStateOf(1)
        setContent {
            Label(
                "Legend",
                modifier = if (aligned == 1) SwingModifier.horizontalAlignment(SwingConstants.RIGHT) else SwingModifier,
            )
        }

        val label = onNodeOfType<JLabel>().fetch()
        val declared = label.horizontalAlignment

        aligned = 0
        awaitIdle()

        // A bare label leads its text, which is what the element captured before it applied.
        assertEquals(SwingConstants.RIGHT, declared, "the alignment applied while declared")
        assertEquals(SwingConstants.LEADING, label.horizontalAlignment, "dropping it restores the label's own")
    }

    @Test
    fun aComponentWithNoContentPlacementIsRejected() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent { FlowPanel(modifier = SwingModifier.iconTextGap(GAP)) }
                }
            }

        assertTrue(
            "iconTextGap" in failure.message.orEmpty(),
            "the message should name the property: ${failure.message}",
        )
    }

    private companion object {
        const val GAP = 11
    }
}
