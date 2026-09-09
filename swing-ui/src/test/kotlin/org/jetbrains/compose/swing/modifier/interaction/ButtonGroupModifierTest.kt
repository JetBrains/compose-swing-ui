package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isSelected
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.ButtonGroup
import javax.swing.DefaultButtonModel
import javax.swing.JCheckBoxMenuItem
import javax.swing.JRadioButton
import javax.swing.JRadioButtonMenuItem
import javax.swing.JToggleButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the button-group membership a caller declares on buttons it lays out itself.
 * Each test asserts what an observer of the live Swing tree sees: which buttons are one choice, which
 * group a button's model belongs to, that a button stops taking part in the exclusion once it no
 * longer declares the group, and what a member reports when the user clicks it.
 */
class ButtonGroupModifierTest {
    @Test
    fun buttonsDeclaredWithOneGroupAreOneChoice() = runComposeSwingTest {
        var choice by mutableIntStateOf(0)
        var label by mutableStateOf("first")
        lateinit var group: ButtonGroup
        setContent {
            group = remember { ButtonGroup() }
            Panel(modifier = SwingModifier.name(label)) {
                listOf("Small", "Medium", "Large").forEachIndexed { index, text ->
                    RadioButton(
                        text = text,
                        selected = choice == index,
                        onSelectedChange = { choice = index },
                        modifier = SwingModifier.buttonGroup(group),
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
    fun aPickTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        setContent {
            val group = remember { ButtonGroup() }
            Panel {
                RadioButton(
                    text = "A",
                    selected = true,
                    onSelectedChange = {},
                    modifier = SwingModifier.buttonGroup(group),
                )
                RadioButton(
                    text = "B",
                    selected = false,
                    onSelectedChange = {},
                    modifier = SwingModifier.buttonGroup(group),
                )
            }
        }
        onNodeWithText("A").assert(isSelected())

        onNodeWithText("B").performClick()
        awaitIdle()

        // The group clears the member that held the selection without that member being clicked, so
        // the declaration standing on it is what takes the selection back.
        onNodeWithText("A").assert(isSelected())
        onNodeWithText("B").assert(isSelected(false))
    }

    @Test
    fun aButtonComposedSelectedTakesTheSelectionAsItJoins() = runComposeSwingTest {
        var choice by mutableStateOf("A")
        setContent {
            val group = remember { ButtonGroup() }
            Panel {
                RadioButton(
                    text = "A",
                    selected = choice == "A",
                    onSelectedChange = { choice = "A" },
                    modifier = SwingModifier.buttonGroup(group),
                )
                if (choice == "C") {
                    RadioButton(
                        text = "C",
                        selected = true,
                        onSelectedChange = { choice = "C" },
                        modifier = SwingModifier.buttonGroup(group),
                    )
                }
            }
        }
        onNodeWithText("A").assert(isSelected())

        choice = "C"
        awaitIdle()

        // A group clears a member that arrives selected while another one still holds the selection, so
        // what the newcomer declares would be lost the moment it joined.
        onNodeWithText("C").assert(isSelected())
        onNodeWithText("A").assert(isSelected(false))
    }

    @Test
    fun clickingTheMemberThatHoldsTheSelectionReportsItStillSelected() = runComposeSwingTest {
        var choice by mutableStateOf("A")
        val reports = mutableListOf<Pair<String, Boolean>>()
        setContent {
            val group = remember { ButtonGroup() }
            Panel {
                listOf("A", "B").forEach { text ->
                    RadioButton(
                        text = text,
                        selected = choice == text,
                        onSelectedChange = {
                            reports += text to it
                            choice = text
                        },
                        modifier = SwingModifier.buttonGroup(group),
                    )
                }
            }
        }

        onNodeWithText("B").performClick()
        awaitIdle()
        assertEquals(
            listOf("B" to true),
            reports,
            "the member the group moves the selection to should report it selected",
        )

        onNodeWithText("B").performClick()
        awaitIdle()

        // A group only ever moves its selection, so a click on the member already holding it changes
        // nothing and Swing fires the activation alone. The button reports that activation with the
        // selection it stands on, rather than with a clearing the group never made. The member the
        // group cleared on the first click reports nothing: it was never activated.
        assertEquals(
            listOf("B" to true, "B" to true),
            reports,
            "the second click on the member holding the selection should report it as still selected",
        )
        onNodeWithText("B").assert(isSelected())
        onNodeWithText("A").assert(isSelected(false))
    }

    @Test
    fun aGroupedCheckBoxClickedAgainStaysChecked() = runComposeSwingTest {
        var choice by mutableStateOf("Metric")
        val reports = mutableListOf<Pair<String, Boolean>>()
        setContent {
            val group = remember { ButtonGroup() }
            Panel {
                listOf("Metric", "Imperial").forEach { text ->
                    CheckBox(
                        text = text,
                        checked = choice == text,
                        onCheckedChange = {
                            reports += text to it
                            choice = text
                        },
                        modifier = SwingModifier.buttonGroup(group),
                    )
                }
            }
        }

        onNodeWithText("Imperial").performClick()
        awaitIdle()
        assertEquals(
            listOf("Imperial" to true),
            reports,
            "the box the group moves the selection to should report it checked",
        )

        onNodeWithText("Imperial").performClick()
        awaitIdle()

        // Boxes are independent of one another until a group holds them: in one, a box clicked while
        // checked has no member to hand the check to, so it stays checked and reports the activation
        // Swing raises for it.
        assertEquals(
            listOf("Imperial" to true, "Imperial" to true),
            reports,
            "the second click on the checked box should report it as still checked",
        )
        onNodeWithText("Imperial").assert(isSelected())
        onNodeWithText("Metric").assert(isSelected(false))
    }

    @Test
    fun aGroupedCheckBoxMenuItemClickedAgainStaysChecked() = runComposeSwingTest {
        var choice by mutableStateOf("Metric")
        val reports = mutableListOf<Pair<String, Boolean>>()
        val popup =
            composeMenu {
                val group = remember { ButtonGroup() }
                listOf("Metric", "Imperial").forEach { text ->
                    CheckBoxMenuItem(
                        text = text,
                        checked = choice == text,
                        onCheckedChange = {
                            reports += text to it
                            choice = text
                        },
                        modifier = SwingModifier.buttonGroup(group),
                    )
                }
            }
        val imperial = popup.getComponent(1) as JCheckBoxMenuItem

        imperial.doClick()
        awaitIdle()
        assertEquals(
            listOf("Imperial" to true),
            reports,
            "the item the group moves the selection to should report it checked",
        )

        imperial.doClick()
        awaitIdle()

        assertEquals(
            listOf("Imperial" to true, "Imperial" to true),
            reports,
            "the second click on the checked item should report it as still checked",
        )
        assertTrue(imperial.isSelected, "the item holding the selection stays checked")
        assertFalse((popup.getComponent(0) as JCheckBoxMenuItem).isSelected, "the cleared item stays cleared")
    }

    @Test
    fun aGroupedRadioButtonMenuItemClickedAgainStaysSelected() = runComposeSwingTest {
        var choice by mutableStateOf("Metric")
        val reports = mutableListOf<Pair<String, Boolean>>()
        val popup =
            composeMenu {
                val group = remember { ButtonGroup() }
                listOf("Metric", "Imperial").forEach { text ->
                    RadioButtonMenuItem(
                        text = text,
                        selected = choice == text,
                        onSelectedChange = {
                            reports += text to it
                            choice = text
                        },
                        modifier = SwingModifier.buttonGroup(group),
                    )
                }
            }
        val imperial = popup.getComponent(1) as JRadioButtonMenuItem

        imperial.doClick()
        awaitIdle()
        assertEquals(
            listOf("Imperial" to true),
            reports,
            "the item the group moves the selection to should report it selected",
        )

        imperial.doClick()
        awaitIdle()

        assertEquals(
            listOf("Imperial" to true, "Imperial" to true),
            reports,
            "the second click on the item holding the selection should report it as still selected",
        )
        assertTrue(imperial.isSelected, "the item holding the selection stays selected")
        assertFalse((popup.getComponent(0) as JRadioButtonMenuItem).isSelected, "the cleared item stays cleared")
    }

    @Test
    fun aGroupedToggleButtonClickedAgainStaysIn() = runComposeSwingTest {
        val reports = mutableListOf<Pair<String, Boolean>>()
        setContent {
            val group = remember { ButtonGroup() }
            Panel {
                ToggleButton(
                    text = "Bold",
                    selected = true,
                    onSelectedChange = { reports += "Bold" to it },
                    modifier = SwingModifier.buttonGroup(group),
                )
                ToggleButton(
                    text = "Italic",
                    selected = false,
                    onSelectedChange = { reports += "Italic" to it },
                    modifier = SwingModifier.buttonGroup(group),
                )
            }
        }
        onNodeWithText("Bold").assert(isSelected())

        onNodeWithText("Bold").performClick()
        awaitIdle()

        // Standing alone a toggle button comes out when it is clicked while in; in a group there is no
        // member for the selection to move to, so the button stays in. Only the button the user
        // pressed raises an activation, so it alone reports.
        assertEquals(
            listOf("Bold" to true),
            reports,
            "a click on the toggle button holding the selection should report it as still selected",
        )
        onNodeWithText("Bold").assert(isSelected())
        onNodeWithText("Italic").assert(isSelected(false))
    }

    @Test
    fun groupsDeclaredSeparatelyAreSeparateChoices() = runComposeSwingTest {
        var left by mutableIntStateOf(0)
        var right by mutableIntStateOf(0)
        setContent {
            val leftGroup = remember { ButtonGroup() }
            val rightGroup = remember { ButtonGroup() }
            Panel {
                listOf("L0", "L1").forEachIndexed { index, text ->
                    RadioButton(
                        text = text,
                        selected = left == index,
                        onSelectedChange = { left = index },
                        modifier = SwingModifier.buttonGroup(leftGroup),
                    )
                }
                listOf("R0", "R1").forEachIndexed { index, text ->
                    RadioButton(
                        text = text,
                        selected = right == index,
                        onSelectedChange = { right = index },
                        modifier = SwingModifier.buttonGroup(rightGroup),
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
            Panel {
                RadioButton(
                    text = "A",
                    selected = false,
                    onSelectedChange = {},
                    modifier = SwingModifier.buttonGroup(first),
                )
                RadioButton(
                    text = "B",
                    selected = false,
                    onSelectedChange = {},
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
            Panel {
                RadioButton(
                    text = "Kept",
                    selected = false,
                    onSelectedChange = {},
                    modifier = SwingModifier.buttonGroup(group),
                )
                if (showExtra) {
                    RadioButton(
                        text = "Extra",
                        selected = false,
                        onSelectedChange = {},
                        modifier = SwingModifier.buttonGroup(group),
                    )
                }
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
                selected = false,
                onSelectedChange = {},
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
            "the button must leave the group once the membership declaration goes",
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
