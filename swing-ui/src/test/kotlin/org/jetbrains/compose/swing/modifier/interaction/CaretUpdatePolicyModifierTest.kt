package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextArea
import javax.swing.text.DefaultCaret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Behavioral coverage for the `SwingModifier.caretUpdatePolicy` switch. Each case puts the caret at a
 * known offset, has text arrive ahead of it, and asserts where the caret ends up - the difference
 * between a log view that scrolls itself along and one that stays where the reader left it.
 *
 * The area is driven straight through its document rather than through a declared value, so the caret
 * is the only thing reacting to the edit.
 */
class CaretUpdatePolicyModifierTest {
    @Test
    fun neverUpdateHoldsTheCaretWhereItIs() = runComposeSwingTest {
        val area = areaWithPolicy(DefaultCaret.NEVER_UPDATE)

        area.caretPosition = TEXT.length
        area.document.insertString(0, PREFIX, null)
        awaitIdle()

        assertEquals(11, area.caretPosition, "the caret should stay at its offset while text arrives above")
    }

    @Test
    fun anUndeclaredCaretFollowsAnEditOnTheEventThread() = runComposeSwingTest {
        val area = areaWith(SwingModifier)

        area.caretPosition = TEXT.length
        area.document.insertString(0, PREFIX, null)
        awaitIdle()

        assertEquals(
            21,
            area.caretPosition,
            "a caret left to its own policy follows an edit made on the event thread",
        )
    }

    @Test
    fun alwaysUpdateCarriesTheCaretForAnEditOffTheEventThread() = runComposeSwingTest {
        val area = areaWithPolicy(DefaultCaret.ALWAYS_UPDATE)

        area.caretPosition = TEXT.length
        area.insertPrefixOffTheEventThread()
        awaitIdle()

        assertEquals(
            21,
            area.caretPosition,
            "ALWAYS_UPDATE should carry the caret along with an edit made off the event thread",
        )
    }

    @Test
    fun theCaretsOwnPolicyLeavesAnEditOffTheEventThreadAlone() = runComposeSwingTest {
        val area = areaWithPolicy(DefaultCaret.UPDATE_WHEN_ON_EDT)

        area.caretPosition = TEXT.length
        area.insertPrefixOffTheEventThread()
        awaitIdle()

        assertEquals(
            11,
            area.caretPosition,
            "UPDATE_WHEN_ON_EDT should leave the caret alone for an edit made off the event thread",
        )
    }

    @Test
    fun aChangedPolicyIsApplied() = runComposeSwingTest {
        var policy by mutableStateOf(DefaultCaret.NEVER_UPDATE)
        setContent {
            SwingNode(
                factory = { JTextArea(TEXT) },
                modifier = SwingModifier.caretUpdatePolicy(policy),
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()
        area.caretPosition = TEXT.length
        area.document.insertString(0, PREFIX, null)
        awaitIdle()
        assertEquals(11, area.caretPosition, "the caret should be held while NEVER_UPDATE is declared")

        policy = DefaultCaret.ALWAYS_UPDATE
        awaitIdle()
        area.document.insertString(0, PREFIX, null)
        awaitIdle()

        assertEquals(
            21,
            area.caretPosition,
            "the recomposed policy should reach the caret and let the next edit carry it along",
        )
    }

    @Test
    fun droppingTheModifierPutsBackTheCaretsPreviousPolicy() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextArea(TEXT) },
                modifier = if (declared) SwingModifier.caretUpdatePolicy(DefaultCaret.NEVER_UPDATE) else SwingModifier,
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()

        declared = false
        awaitIdle()
        area.caretPosition = TEXT.length
        area.document.insertString(0, PREFIX, null)
        awaitIdle()

        assertEquals(
            21,
            area.caretPosition,
            "leaving the modifier should put back the policy the caret carried, which follows the edit again",
        )
    }

    @Test
    fun aPolicyDeclaredBeforeTheCaretReachesItAndComesApartAfterIt() = runComposeSwingTest {
        val mine = DefaultCaret()
        var decorated by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextArea(TEXT) },
                modifier =
                    if (decorated) {
                        SwingModifier.caretUpdatePolicy(DefaultCaret.NEVER_UPDATE).caret(mine)
                    } else {
                        SwingModifier
                    },
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()
        val installedPolicy = (JTextArea().caret as DefaultCaret).updatePolicy
        assertEquals(
            DefaultCaret.NEVER_UPDATE,
            mine.updatePolicy,
            "the policy declared first reaches the caret declared after it",
        )

        // The caret is declared last, so it comes apart first: the area carries its own caret again
        // while the policy declaration still stands, and the policy hands that caret back what it read.
        decorated = false
        awaitIdle()

        assertNotSame(mine, area.caret, "the caret the area carried before is put back")
        assertEquals(
            installedPolicy,
            (area.caret as DefaultCaret).updatePolicy,
            "the caret put back keeps the policy it carried",
        )
        assertEquals(
            DefaultCaret.NEVER_UPDATE,
            mine.updatePolicy,
            "the caret let go of keeps the policy it was given",
        )
    }

    @Test
    fun aCaretReplacingAnotherUnderTheSamePolicyHoldsToIt() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextArea(TEXT) },
                modifier =
                    SwingModifier
                        .caret(if (swapped) second else first)
                        .caretUpdatePolicy(DefaultCaret.NEVER_UPDATE),
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()

        // The caret slot writes on this pass, so the policy slot declared after it is written again
        // rather than adopted, onto the caret that has just arrived.
        swapped = true
        awaitIdle()
        assertSame(second, area.caret, "the newly declared caret should reach the area")

        area.caretPosition = TEXT.length
        area.document.insertString(0, PREFIX, null)
        awaitIdle()

        assertEquals(
            11,
            area.caretPosition,
            "the policy still declared should hold the caret that replaced the first one where it is",
        )
    }

    /** Composes an area carrying [policy] and returns the live component. */
    private suspend fun ComposeSwingTest.areaWithPolicy(policy: Int): JTextArea =
        areaWith(SwingModifier.caretUpdatePolicy(policy))

    /** Composes an area carrying [modifier] and returns the live component. */
    private suspend fun ComposeSwingTest.areaWith(modifier: SwingModifier): JTextArea {
        setContent {
            SwingNode(factory = { JTextArea(TEXT) }, modifier = modifier)
        }
        awaitIdle()
        return onNodeOfType<JTextArea>().fetch()
    }

    /**
     * Inserts [PREFIX] at the head of the document from another thread and waits for it to land - the
     * shape of an appender writing into a log view while the event thread is elsewhere.
     */
    private fun JTextArea.insertPrefixOffTheEventThread() {
        val appender = Thread { document.insertString(0, PREFIX, null) }
        appender.start()
        appender.join()
    }
}

private const val TEXT = "hello world"
private const val PREFIX = "appended: "
