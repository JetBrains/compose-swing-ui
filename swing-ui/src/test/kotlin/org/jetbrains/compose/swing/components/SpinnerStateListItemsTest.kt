package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSpinner
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [Spinner] declared over a list the caller keeps and mutates, rather than one rebuilt each pass.
 *
 * A `JSpinner` repaints what its model tells it changed. A model answering the caller's own list hands
 * the new items to anything that asks, so a value read back off the model is no evidence the spinner was
 * told: these tests assert on the change events the model fires.
 */
class SpinnerStateListItemsTest {
    @Test
    fun addingToADeclaredStateListNotifiesTheSpinner() = runComposeSwingTest {
        val items = mutableStateListOf("Ada")
        setContent { Spinner(items = items, value = "Ada", onValueChange = {}) }

        val spinner = onNodeOfType<JSpinner>().fetch()
        val changes = spinner.recordChanges()

        items.add("Alan")
        awaitIdle()

        assertTrue(changes.isNotEmpty(), "the spinner was never told its items changed")
        assertEquals("Alan", spinner.model.nextValue, "the spinner should step onto the new item")
    }

    @Test
    fun removingFromADeclaredStateListNotifiesTheSpinner() = runComposeSwingTest {
        val items = mutableStateListOf("Ada", "Alan")
        setContent { Spinner(items = items, value = "Ada", onValueChange = {}) }

        val spinner = onNodeOfType<JSpinner>().fetch()
        val changes = spinner.recordChanges()

        items.removeAt(1)
        awaitIdle()

        assertTrue(changes.isNotEmpty(), "the spinner was never told its items changed")
        assertEquals(null, spinner.model.nextValue, "the spinner should have nothing left to step onto")
    }

    @Test
    fun theModelAnswersOutOfItsOwnItemsUntilAPassCarriesTheChange() = runComposeSwingTest {
        val items = mutableStateListOf("Ada", "Alan")
        setContent { Spinner(items = items, value = "Ada", onValueChange = {}) }
        awaitIdle()
        mainClock.autoAdvance = false

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals("Alan", spinner.model.nextValue, "the spinner should start out able to step onto Alan")

        // The caller drops the item the spinner can step onto, and no pass has carried that yet. A model
        // reading the caller's list would answer out of it here - including during a paint, which Swing
        // schedules whenever it likes rather than when a pass says so.
        items.removeAt(1)
        awaitIdle()

        assertEquals(
            "Alan",
            spinner.model.nextValue,
            "the model must answer out of the items the spinner was last told about, not the caller's list",
        )

        mainClock.advanceTimeByFrame()

        assertEquals(
            null,
            spinner.model.nextValue,
            "the pass carrying the change should leave the model with nothing to step onto",
        )
    }

    @Test
    fun aPassThatChangesNoItemNotifiesNothing() = runComposeSwingTest {
        val items = mutableStateListOf("Ada", "Alan")
        var tip by mutableStateOf("Pick a name")
        setContent {
            Spinner(items = items, value = "Ada", onValueChange = {}, modifier = SwingModifier.toolTip(tip))
        }

        val spinner = onNodeOfType<JSpinner>().fetch()
        val changes = spinner.recordChanges()

        tip = "Who is who"
        awaitIdle()

        assertEquals("Who is who", spinner.toolTipText, "the pass this asserts about has to have run")
        assertTrue(changes.isEmpty(), "a pass that changed no item should leave the spinner alone")
    }
}

/** Records every change the spinner reports from now on, newest last. */
private fun JSpinner.recordChanges(): List<ChangeEvent> {
    val changes = mutableListOf<ChangeEvent>()
    addChangeListener(ChangeListener { changes += it })
    return changes
}
