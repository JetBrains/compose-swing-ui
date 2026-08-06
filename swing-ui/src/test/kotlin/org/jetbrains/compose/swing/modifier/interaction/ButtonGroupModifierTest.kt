package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isSelected
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.ButtonGroup
import javax.swing.DefaultButtonModel
import javax.swing.JRadioButton
import javax.swing.JToggleButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioural coverage for the button-group membership a caller declares on buttons it lays out itself.
 * Each test asserts what an observer of the live Swing tree sees: which buttons are one choice, which
 * group a button's model belongs to, and that a button stops taking part in the exclusion once it no
 * longer declares the group.
 */
class ButtonGroupModifierTest {
    @Test
    fun buttonsDeclaredWithOneGroupAreOneChoice() = runComposeSwingTest {
        var choice by mutableIntStateOf(0)
        var label by mutableStateOf("first")
        lateinit var group: ButtonGroup
        setContent {
            group = remember { ButtonGroup() }
            Column(modifier = SwingModifier.name(label)) {
                listOf("Small", "Medium", "Large").forEachIndexed { index, text ->
                    RadioButton(
                        text = text,
                        modifier = SwingModifier.buttonGroup(group),
                        selected = choice == index,
                        onSelectedChange = { choice = index },
                    )
                }
            }
        }
        assertEquals(3, group.buttonCount, "every declared button should have joined the group")
        onAllNodesOfType<JRadioButton>().filterToOne(isSelected()).assertTextEquals("Small")

        onNodeWithText("Medium").performClick()
        awaitIdle()

        // The group holds the selection to one member: picking it clears whichever member had it.
        onAllNodesOfType<JRadioButton>().filterToOne(isSelected()).assertTextEquals("Medium")

        // Membership is not a one-off of the first pass: after an unrelated recomposition the buttons
        // are still one choice.
        label = "second"
        awaitIdle()
        onNodeWithText("Large").performClick()
        awaitIdle()
        onAllNodesOfType<JRadioButton>().filterToOne(isSelected()).assertTextEquals("Large")
    }

    @Test
    fun groupsDeclaredSeparatelyAreSeparateChoices() = runComposeSwingTest {
        var left by mutableIntStateOf(0)
        var right by mutableIntStateOf(0)
        setContent {
            val leftGroup = remember { ButtonGroup() }
            val rightGroup = remember { ButtonGroup() }
            Column {
                listOf("L0", "L1").forEachIndexed { index, text ->
                    RadioButton(
                        text = text,
                        modifier = SwingModifier.buttonGroup(leftGroup),
                        selected = left == index,
                        onSelectedChange = { left = index },
                    )
                }
                listOf("R0", "R1").forEachIndexed { index, text ->
                    RadioButton(
                        text = text,
                        modifier = SwingModifier.buttonGroup(rightGroup),
                        selected = right == index,
                        onSelectedChange = { right = index },
                    )
                }
            }
        }

        onNodeWithText("L1").performClick()
        onNodeWithText("R1").performClick()
        awaitIdle()

        // Exclusion reaches the members of one group only, so each group keeps its own selection.
        onAllNodesOfType<JRadioButton>().filter(isSelected()).assertCountEquals(2)
        onNodeWithText("L0").assert(isSelected(false))
        onNodeWithText("R0").assert(isSelected(false))
    }

    @Test
    fun declaringADifferentGroupMovesTheButton() = runComposeSwingTest {
        var moved by mutableStateOf(false)
        lateinit var first: ButtonGroup
        lateinit var second: ButtonGroup
        setContent {
            first = remember { ButtonGroup() }
            second = remember { ButtonGroup() }
            Column {
                RadioButton(text = "A", modifier = SwingModifier.buttonGroup(first))
                RadioButton(
                    text = "B",
                    modifier = SwingModifier.buttonGroup(if (moved) second else first),
                )
            }
        }
        assertEquals(2, first.buttonCount, "both buttons start in the group they declare")

        moved = true
        awaitIdle()

        val moving = onNodeWithText("B").fetch<JRadioButton>()
        assertEquals(1, first.buttonCount, "the button leaves the group it stopped declaring")
        assertEquals(1, second.buttonCount, "and joins the one it declares now")
        assertSame(
            second,
            (moving.model as DefaultButtonModel).group,
            "the button's own model should name the group it moved to",
        )
    }

    @Test
    fun aButtonThatLeavesTheCompositionLeavesTheGroup() = runComposeSwingTest {
        var showExtra by mutableStateOf(true)
        lateinit var group: ButtonGroup
        setContent {
            group = remember { ButtonGroup() }
            Column {
                RadioButton(text = "Kept", modifier = SwingModifier.buttonGroup(group))
                if (showExtra) RadioButton(text = "Extra", modifier = SwingModifier.buttonGroup(group))
            }
        }
        val extra = onNodeWithText("Extra").fetch<JRadioButton>()

        showExtra = false
        awaitIdle()

        // A departed button must not stay bound: a lingering member would keep taking part in the
        // exclusion the surviving buttons enforce.
        assertEquals(1, group.buttonCount, "the group should hold only the buttons still declared")
        assertNull((extra.model as DefaultButtonModel).group, "a departed button should leave the group")
    }

    @Test
    fun droppingTheModifierLeavesTheGroup() = runComposeSwingTest {
        var grouped by mutableStateOf(true)
        lateinit var group: ButtonGroup
        setContent {
            group = remember { ButtonGroup() }
            ToggleButton(
                text = "Bold",
                modifier = if (grouped) SwingModifier.buttonGroup(group) else SwingModifier,
            )
        }
        val toggle = onNodeWithText("Bold").fetch<JToggleButton>()
        assertSame(group, (toggle.model as DefaultButtonModel).group, "the button starts in the group")

        grouped = false
        awaitIdle()

        assertEquals(0, group.buttonCount, "the group should be left empty")
        assertNull(
            (toggle.model as DefaultButtonModel).group,
            "the button must leave the group once the modifier leaves the chain",
        )
    }

    @Test
    fun aComponentThatIsNotAButtonIsRejected() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label("X", modifier = SwingModifier.buttonGroup(ButtonGroup()))
                }
                awaitIdle()
            }
        val message = error.message.orEmpty()
        assertTrue(
            "javax.swing.AbstractButton" in message,
            "the wrong-target error must name the required button target, but was: $message",
        )
    }
}
