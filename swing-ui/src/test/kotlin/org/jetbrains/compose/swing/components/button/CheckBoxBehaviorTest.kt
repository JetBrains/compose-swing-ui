package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.click
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.singleWidget
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.JCheckBox
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral coverage for both [CheckBox] overloads, asserted on the live `JCheckBox`.
 *
 * The checked state is controlled: the box shows whatever `checked` holds, a click reports the new
 * state through the callback, and a value pushed in from composition applies without echoing back as a
 * callback. Text, checked state and the modifier are each driven through more than one value and back,
 * so a parameter honored only when the component is built would fail here. A click the caller does not
 * adopt does not stand - the next settled pass writes the declared state back over it - and a click it
 * does adopt is left standing, both by the time the click returns.
 *
 * A declared checked state is settled by the pass that declares it and no later one. The same
 * settle serves `RadioButton` and `ToggleButton`, which reach it through the shared `declareSelected`.
 */
class CheckBoxBehaviorTest {
    @Test
    fun theTextFollowsTheStateDrivingIt() = runComposeSwingTest {
        var text by mutableStateOf("Word wrap")
        setContent { CheckBox(text = text, checked = false, onCheckedChange = {}) }

        onNodeOfType<JCheckBox>().assertTextEquals("Word wrap")

        text = "Wrap lines"
        awaitIdle()
        onNodeOfType<JCheckBox>().assertTextEquals("Wrap lines")

        text = "Word wrap"
        awaitIdle()
        onNodeOfType<JCheckBox>().assertTextEquals("Word wrap")
    }

    @Test
    fun theCheckedStateFollowsTheStateDrivingIt() = runComposeSwingTest {
        // A JCheckBox is built unchecked, so declaring `true` first proves the parameter reaches the
        // box on the very first composition rather than the box merely keeping its own default.
        var checked by mutableStateOf(true)
        setContent { CheckBox(text = "Word wrap", checked = checked, onCheckedChange = {}) }
        awaitIdle()
        mainClock.autoAdvance = false

        val box = onNodeOfType<JCheckBox>().fetch()
        assertTrue(box.isSelected, "the box should open on the checked state it first declares")

        checked = false
        awaitIdle()
        mainClock.advanceTimeByFrame()
        assertFalse(box.isSelected, "the pass declaring the box clear should leave it clear, not still checked")

        checked = true
        awaitIdle()
        mainClock.advanceTimeByFrame()
        assertTrue(box.isSelected, "the pass declaring the box checked again should leave it checked, not clear")
    }

    @Test
    fun clickingReportsTheNewCheckedStateAndTheBoxKeepsIt() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            CheckBox(
                text = "Word wrap",
                checked = checked,
                onCheckedChange = {
                    reported += it
                    checked = it
                },
            )
        }

        val checkBox = onNodeOfType<JCheckBox>()
        checkBox.performClick()
        assertEquals(listOf(true), reported, "the click reports the new checked state")
        checkBox.assert(SwingMatcher.isSelected())

        checkBox.performClick()
        assertEquals(listOf(true, false), reported, "the second click reports the box being cleared")
        checkBox.assert(SwingMatcher.isSelected(false))
    }

    @Test
    fun aCheckedStatePushedFromCompositionDoesNotFireTheCallback() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            CheckBox(text = "Word wrap", checked = checked, onCheckedChange = { reported += it })
        }

        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assert(SwingMatcher.isSelected())
        assertEquals(emptyList(), reported, "only a user gesture reports through the callback")
    }

    @Test
    fun aClickRunsTheMostRecentlyComposedCallback() = runComposeSwingTest {
        var round by mutableStateOf(1)
        val runs = mutableListOf<Int>()
        setContent {
            // The round is captured at composition time, so every pass declares a callback that
            // reports a different value: a callback captured once when the box was built keeps
            // reporting the round it was born in, however often the declaration changes.
            val current = round
            CheckBox(text = "Word wrap", checked = false, onCheckedChange = { runs += current })
        }

        onNodeOfType<JCheckBox>().performClick()
        assertEquals(listOf(1), runs, "the click runs the composed callback")

        round = 2
        awaitIdle()
        onNodeOfType<JCheckBox>().performClick()
        assertEquals(listOf(1, 2), runs, "the click runs the recomposed callback, not the one it replaced")
    }

    @Test
    fun recompositionLeavesExactlyOneListenerOnTheBox() = runComposeSwingTest {
        var text by mutableStateOf("One")
        val reported = mutableListOf<Boolean>()
        setContent { CheckBox(text = text, checked = false, onCheckedChange = { reported += it }) }

        text = "Two"
        awaitIdle()
        text = "Three"
        awaitIdle()

        assertEquals(
            1,
            onNodeOfType<JCheckBox>().fetch().actionListeners.size,
            "recomposition must not stack up action listeners",
        )
        onNodeOfType<JCheckBox>().performClick()
        assertEquals(listOf(true), reported, "a single click reports exactly once")
    }

    @Test
    fun theModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Wraps long lines")
        setContent {
            CheckBox(
                text = "Word wrap",
                checked = false,
                onCheckedChange = {},
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val box = onNodeOfType<JCheckBox>().fetch()
        assertEquals("Wraps long lines", box.toolTipText, "the modifier applies to the box")

        tip = "Wraps text at the viewport edge"
        awaitIdle()
        assertEquals("Wraps text at the viewport edge", box.toolTipText, "the modifier follows its state")

        tip = null
        awaitIdle()
        assertNull(box.toolTipText, "dropping the element restores the tooltip the box had without it")
    }

    @Test
    fun theRawListenerOverloadAppliesTextAndCheckedStateAndFires() = runComposeSwingTest {
        var text by mutableStateOf("Word wrap")
        var checked by mutableStateOf(false)
        var tip by mutableStateOf<String?>("Wraps long lines")
        val reported = mutableListOf<Boolean>()
        setContent {
            val listener = remember { ActionListener { event -> reported += (event.source as JCheckBox).isSelected } }
            CheckBox(
                text = text,
                checked = checked,
                actionListener = listener,
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val checkBox = onNodeOfType<JCheckBox>()
        checkBox.assertTextEquals("Word wrap")
        checkBox.assert(SwingMatcher.isSelected(false))

        checked = true
        text = "Wrap lines"
        tip = null
        awaitIdle()
        checkBox.assert(SwingMatcher.isSelected())
        checkBox.assertTextEquals("Wrap lines")
        val box = checkBox.fetch()
        assertNull(box.toolTipText, "dropping the element restores the tooltip the box had without it")
        assertEquals(1, box.actionListeners.size, "a stable listener instance stays attached exactly once")

        checked = false
        awaitIdle()
        checkBox.assert(SwingMatcher.isSelected(false))

        checkBox.performClick()
        assertEquals(listOf(true), reported, "the raw listener sees the state the click produced")
    }

    @Test
    fun swappingTheRawListenerHandsClicksToTheNewInstance() = runComposeSwingTest {
        var second by mutableStateOf(false)
        var first = 0
        var latest = 0
        setContent {
            val firstListener = remember { ActionListener { first++ } }
            val secondListener = remember { ActionListener { latest++ } }
            CheckBox(
                text = "Word wrap",
                checked = false,
                actionListener = if (second) secondListener else firstListener,
            )
        }

        onNodeOfType<JCheckBox>().performClick()
        assertEquals(1, first, "the declared listener receives the click")

        second = true
        awaitIdle()
        assertEquals(
            1,
            onNodeOfType<JCheckBox>().fetch().actionListeners.size,
            "the replaced listener must be detached, not stacked",
        )

        onNodeOfType<JCheckBox>().performClick()
        assertEquals(1, latest, "the newly declared listener receives the click")
        assertEquals(1, first, "the listener that left the declaration no longer fires")
    }

    @Test
    fun aClickTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var text by mutableStateOf("Word wrap")
        setContent { CheckBox(text = text, checked = false, onCheckedChange = {}) }

        val checkBox = onNodeOfType<JCheckBox>()
        checkBox.performClick()
        checkBox.assert(SwingMatcher.isSelected(false))

        // An unrelated recomposition changes nothing here: the box was already showing the
        // declared state right after the click, not just once this pass ran.
        text = "Wrap lines"
        awaitIdle()
        checkBox.assert(SwingMatcher.isSelected(false))
    }

    @Test
    fun aClickTheCallerAdoptsLeavesTheBoxOnWhatTheCallerAdopted() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var mounted: DisposableHandle? = null
        try {
            var checked by mutableStateOf(false)
            mounted =
                composition.setContent(parent = recomposer.compositionContext) {
                    CheckBox(text = "Word wrap", checked = checked, onCheckedChange = { checked = it })
                }
            val box = singleWidget(composition, JCheckBox::class.java)

            box.doClick(0)

            // Read with no cycle in between: the click's own settle is what has to leave the box here,
            // not a pass a later event brings.
            assertTrue(
                box.isSelected,
                "a click the caller adopts must be settled on what the caller wrote, not put back on " +
                    "the declaration that click replaced",
            )
        } finally {
            mounted?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun anActionListenerTheCallerAddedReadsTheStateTheClickProduced() = runComposeSwingTest {
        val seen = mutableListOf<Boolean>()
        setContent {
            CheckBox(
                text = "Word wrap",
                checked = false,
                // Never adopted, so the wrapper settles the box back to false on the same click.
                onCheckedChange = { },
                modifier =
                    SwingModifier.actionListener { event ->
                        seen += (event.source as JCheckBox).isSelected
                    },
            )
        }

        onNodeOfType<JCheckBox>().performClick()

        assertEquals(
            listOf(true),
            seen,
            "a listener the caller added must be handed the state its click produced, not the one the " +
                "declaration settles back over it",
        )
        assertFalse(
            onNodeOfType<JCheckBox>().fetch<JCheckBox>().isSelected,
            "the settle this asserts about has to have run: the unadopted click is put back",
        )
    }

    @Test
    fun aClickTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JCheckBox::class.java,
            declared = false,
            content = { report -> CheckBox(text = "Word wrap", checked = false, onCheckedChange = { report() }) },
            change = { it.click() },
            read = { it.isSelected },
        )
    }
}
