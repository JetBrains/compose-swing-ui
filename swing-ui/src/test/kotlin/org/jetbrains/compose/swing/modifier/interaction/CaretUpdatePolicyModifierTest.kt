package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextArea
import javax.swing.text.DefaultCaret
import kotlin.test.Test
import kotlin.test.assertEquals

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

        assertEquals(TEXT.length, area.caretPosition, "the caret should stay at its offset while text arrives above")
    }

    @Test
    fun anUndeclaredCaretFollowsAnEditOnTheEventThread() = runComposeSwingTest {
        val area = areaWith(SwingModifier)

        area.caretPosition = TEXT.length
        area.document.insertString(0, PREFIX, null)
        awaitIdle()

        assertEquals(
            TEXT.length + PREFIX.length,
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
            TEXT.length + PREFIX.length,
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
            TEXT.length,
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
                update = { applyModifier(SwingModifier.caretUpdatePolicy(policy)) },
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()
        area.caretPosition = TEXT.length
        area.document.insertString(0, PREFIX, null)
        awaitIdle()
        assertEquals(TEXT.length, area.caretPosition, "the caret should be held while NEVER_UPDATE is declared")

        policy = DefaultCaret.ALWAYS_UPDATE
        awaitIdle()
        val held = area.caretPosition
        area.document.insertString(0, PREFIX, null)
        awaitIdle()

        assertEquals(
            held + PREFIX.length,
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
                update = {
                    applyModifier(
                        if (declared) SwingModifier.caretUpdatePolicy(DefaultCaret.NEVER_UPDATE) else SwingModifier,
                    )
                },
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()

        declared = false
        awaitIdle()
        area.caretPosition = TEXT.length
        area.document.insertString(0, PREFIX, null)
        awaitIdle()

        assertEquals(
            TEXT.length + PREFIX.length,
            area.caretPosition,
            "leaving the chain should put back the policy the caret carried, which follows the edit again",
        )
    }

    /** Composes an area carrying [policy] and returns the live component. */
    private suspend fun ComposeSwingTest.areaWithPolicy(policy: Int): JTextArea =
        areaWith(SwingModifier.caretUpdatePolicy(policy))

    /** Composes an area carrying [modifier] and returns the live component. */
    private suspend fun ComposeSwingTest.areaWith(modifier: SwingModifier): JTextArea {
        setContent {
            SwingNode(factory = { JTextArea(TEXT) }, update = { applyModifier(modifier) })
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
