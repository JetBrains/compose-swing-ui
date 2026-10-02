package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the caret a text component is given and the rate it blinks at.
 *
 * A caret is a stateful object rather than a value: installing one puts it at offset 0 with nothing
 * selected, so these pin that the declared caret both reaches the component and is installed exactly
 * once, and that the caret the component carried before comes back when the declaration leaves.
 */
class CaretModifierTest {
    @Test
    fun aDeclaredCaretReplacesTheOneTheFieldCarriedAndIsPutBackOnRemoval() = runComposeSwingTest {
        val caret = DefaultCaret()
        var declared by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (declared) SwingModifier.caret(caret) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installed = field.caret
        assertNotSame(caret, installed, "the field starts with the caret its look and feel installed")

        declared = true
        awaitIdle()
        assertSame(caret, field.caret, "the declared caret should reach the field")

        declared = false
        awaitIdle()
        assertSame(installed, field.caret, "dropping the declaration should put back the caret the field carried")
    }

    @Test
    fun theDeclaredCaretIsTheOneTheFieldNavigatesWith() = runComposeSwingTest {
        val caret = DefaultCaret()
        setContent {
            SwingNode(factory = { JTextField(TEXT) }, modifier = SwingModifier.caret(caret))
        }
        val field = onNodeOfType<JTextField>().fetch()

        field.caretPosition = CARET_OFFSET

        assertEquals(CARET_OFFSET, caret.dot, "moving the field's caret should run through the declared caret")
        assertEquals(CARET_OFFSET, field.caretPosition, "the field reports the position of the caret it was given")
    }

    @Test
    fun aCaretRedeclaredAcrossARecompositionKeepsTheSelectionMadeBetweenPasses() = runComposeSwingTest {
        val caret = DefaultCaret()
        var tip by mutableStateOf("first")
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.caret(caret).toolTip(tip),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.select(SELECTION_START, SELECTION_END)

        // The modifier changes, so it is diffed again with the same caret declared in it. Installing that
        // caret a second time would put it at offset 0 and drop what the user selected.
        tip = "second"
        awaitIdle()

        assertSame(caret, field.caret, "the same caret should stay installed across the pass")
        assertEquals(SELECTION_START, field.selectionStart, "the selection made between passes should survive")
        assertEquals(SELECTION_END, field.selectionEnd, "the selection made between passes should survive")
    }

    @Test
    fun aCaretMovedIntoAnotherCompositionKeepsTheSelectionTheUserMade() = runComposeSwingTest {
        val caret = DefaultCaret()
        val field = JTextField(TEXT)
        val host = JPanel()
        var inOuter by mutableStateOf(false)
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember { movableContentOf { SwingNode(factory = { field }, modifier = SwingModifier.caret(caret)) } }
            SwingNode(factory = { host })
            if (inOuter) content()
        }
        awaitIdle()
        val nested = host.setContent(parent = context) { if (!inOuter) content() }
        awaitIdle()
        field.select(SELECTION_START, SELECTION_END)

        inOuter = true
        awaitIdle()

        assertSame(caret, field.caret, "the declared caret should still be installed after the move")
        assertEquals(SELECTION_START, field.selectionStart, "the selection the user made should survive the move")
        assertEquals(SELECTION_END, field.selectionEnd, "the selection the user made should survive the move")
        nested.dispose()
    }

    @Test
    fun droppingADeclaredCaretLeavesTheSelectionTheUserMade() = runComposeSwingTest {
        val caret = DefaultCaret()
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (declared) SwingModifier.caret(caret) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.select(SELECTION_START, SELECTION_END)

        // Putting the field's own caret back installs it, which puts a caret at offset 0 and drops the
        // selection. What the user selected belongs to the text, not to the caret being taken off.
        declared = false
        awaitIdle()

        assertNotSame(caret, field.caret, "the declared caret should be off the field")
        assertEquals(SELECTION_START, field.selectionStart, "the selection the user made should survive")
        assertEquals(SELECTION_END, field.selectionEnd, "the selection the user made should survive")
    }

    @Test
    fun aDeclaredBlinkRateReachesTheCaretAndIsPutBackOnRemoval() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (declared) SwingModifier.caretBlinkRate(0) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installedRate = field.caret.blinkRate
        assertTrue(installedRate > 0, "a look and feel gives the caret it installs a blink rate of its own")

        declared = true
        awaitIdle()
        assertEquals(0, field.caret.blinkRate, "the declared rate should reach the caret")

        declared = false
        awaitIdle()
        assertEquals(installedRate, field.caret.blinkRate, "dropping it should put back the rate the caret carried")
    }

    @Test
    fun aCaretHandedToTheFieldBlinksOnlyOnceItIsGivenARate() = runComposeSwingTest {
        val silent = DefaultCaret()
        val blinking = DefaultCaret()
        setContent {
            SwingNode(factory = { JTextField(TEXT) }, modifier = SwingModifier.caret(silent))
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.caret(blinking).caretBlinkRate(BLINK_RATE),
            )
        }

        assertEquals(0, silent.blinkRate, "a look and feel's blink rate reaches only the caret it created itself")
        assertEquals(BLINK_RATE, blinking.blinkRate, "the rate declared with the caret reaches it")
    }

    @Test
    fun aCaretReplacingAnotherUnderTheSameRateBlinksAtIt() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.caret(if (swapped) second else first).caretBlinkRate(BLINK_RATE),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(BLINK_RATE, first.blinkRate, "the rate declared with the first caret reaches it")

        // The rate is declared unchanged, so its slot writes nothing on this pass - the caret arriving
        // is what the rate has to be written from.
        swapped = true
        awaitIdle()

        assertSame(second, field.caret, "the newly declared caret should reach the field")
        assertEquals(BLINK_RATE, second.blinkRate, "the rate still declared should reach the caret that replaced it")
    }

    @Test
    fun aCaretReplacingAnotherIsGivenTheRateDeclaredLast() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        var rate by mutableStateOf(BLINK_RATE)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.caret(if (swapped) second else first).caretBlinkRate(rate),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(BLINK_RATE, first.blinkRate, "the rate declared with the first caret reaches it")

        // The rate changes on a pass of its own, so the caret arriving on the next one is written from
        // what the rate stands at now and not from what it stood at when it was first declared.
        rate = SLOW_BLINK_RATE
        awaitIdle()
        assertEquals(SLOW_BLINK_RATE, first.blinkRate, "the recomposed rate should reach the caret carrying it")

        swapped = true
        awaitIdle()
        assertSame(second, field.caret, "the newly declared caret should reach the field")
        assertEquals(SLOW_BLINK_RATE, second.blinkRate, "the caret replacing it should be given the rate declared last")

        // The declaration still stands over the field, and the caret it let go of is no longer its target.
        rate = BLINK_RATE
        awaitIdle()
        assertEquals(BLINK_RATE, second.blinkRate, "a rate declared later should reach the caret now carried")
        assertEquals(SLOW_BLINK_RATE, first.blinkRate, "the caret let go of should be left at the rate it had")
    }

    @Test
    fun aFieldLettingGoOfItsCaretUnderADeclaredRateIsLeftAlone() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.caretBlinkRate(BLINK_RATE),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installed = field.caret
        assertEquals(BLINK_RATE, installed.blinkRate, "the declared rate reaches the caret the field carries")

        // The field announces the caret it let go of, which is the announcement the declaration is
        // written again on - and it leaves nothing to write to.
        field.caret = null
        assertEquals(BLINK_RATE, installed.blinkRate, "the caret let go of keeps the rate it was given")

        val arriving = DefaultCaret()
        field.caret = arriving
        assertEquals(BLINK_RATE, arriving.blinkRate, "the caret arriving after it is given the declared rate")
    }

    @Test
    fun aRateGoesBackOntoTheCaretTheFieldCarriesRatherThanTheOneItWasReadFrom() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (declared) SwingModifier.caretBlinkRate(BLINK_RATE) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val found = JTextField().caret.blinkRate
        assertEquals(BLINK_RATE, field.caret.blinkRate, "the declared rate reaches the caret the field carries")

        // A look and feel that answers a caret it is handed by installing one of its own is what this
        // stands in for: the caret the rate was read from is gone before the declaration leaves.
        val arriving = DefaultCaret()
        field.caret = arriving
        assertEquals(BLINK_RATE, arriving.blinkRate, "the standing declaration reaches the caret arriving")

        declared = false
        awaitIdle()

        assertEquals(
            found,
            field.caret.blinkRate,
            "the field is left blinking at the rate the modifier found, not at the one it declared",
        )
    }

    @Test
    fun aCaretArrivingAfterTheRateDeclarationIsDroppedIsLeftAlone() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        var declared by mutableStateOf(true)
        setContent {
            val caret = SwingModifier.caret(if (swapped) second else first)
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (declared) caret.caretBlinkRate(BLINK_RATE) else caret,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(BLINK_RATE, first.blinkRate, "the declared rate should reach the caret the field carries")

        // Dropping the rate leaves the field with nothing declaring one, so the caret installed on the
        // next pass is only reached if the dropped declaration is still listening for it.
        declared = false
        awaitIdle()
        assertEquals(0, first.blinkRate, "dropping the rate should put back what the caret carried before it")

        swapped = true
        awaitIdle()
        assertSame(second, field.caret, "the newly declared caret should reach the field")
        assertEquals(0, second.blinkRate, "a caret arriving after the rate is dropped should not be given it")
    }

    @Test
    fun droppingACaretAndItsRateTogetherLeavesEachWhereItStood() = runComposeSwingTest {
        val declared = DefaultCaret()
        var decorated by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (decorated) SwingModifier.caret(declared).caretBlinkRate(BLINK_RATE) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installedRate = JTextField().caret.blinkRate
        assertEquals(BLINK_RATE, declared.blinkRate, "the declared rate reaches the declared caret")

        // The rate answers the caret being replaced by writing itself onto the caret that arrives. The
        // modifier unwinds last-first, so the rate is gone - listener and all - before the caret slot hands
        // the field's own caret back, which is what leaves that caret at the rate it came with.
        decorated = false
        awaitIdle()

        assertNotSame(declared, field.caret, "the caret the field carried before is put back")
        assertEquals(installedRate, field.caret.blinkRate, "the caret put back keeps the rate it carried")
        assertEquals(0, declared.blinkRate, "the declared caret is left at the rate it had")
    }

    @Test
    fun aRateDeclaredBeforeTheCaretReachesItAndComesApartAfterIt() = runComposeSwingTest {
        val mine = DefaultCaret()
        var decorated by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (decorated) SwingModifier.caretBlinkRate(BLINK_RATE).caret(mine) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installedRate = JTextField().caret.blinkRate
        assertEquals(BLINK_RATE, mine.blinkRate, "the rate declared first reaches the caret declared after it")

        // The caret is declared last, so it comes apart first: the field carries its own caret again
        // while the rate declaration still stands, and the rate hands that caret back what it read.
        decorated = false
        awaitIdle()

        assertNotSame(mine, field.caret, "the caret the field carried before is put back")
        assertEquals(installedRate, field.caret.blinkRate, "the caret put back keeps the rate it carried")
        assertEquals(BLINK_RATE, mine.blinkRate, "the caret let go of keeps the rate it was given")
    }

    @Test
    fun aCaretLeavingAndComingBackUnderAStandingRateLeavesTheFieldAtTheDeclaredRate() = runComposeSwingTest {
        val mine = DefaultCaret()
        var declared by mutableStateOf(true)
        var styled by mutableStateOf(true)
        setContent {
            val rate = SwingModifier.caretBlinkRate(BLINK_RATE)
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier =
                    when {
                        !styled -> SwingModifier
                        declared -> SwingModifier.caret(mine).caretBlinkRate(BLINK_RATE)
                        else -> rate
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(BLINK_RATE, mine.blinkRate, "the declared rate reaches the declared caret")

        // The caret leaves the modifier and comes back, so the slot holding it is no longer the older
        // of the two. Whichever slot comes apart first, the rate restores what it read to the caret it
        // read it from. The field's own caret was given the rate while it stood in for the declared
        // one, and keeps it the way any caret the declaration let go of does.
        declared = false
        awaitIdle()
        declared = true
        awaitIdle()
        styled = false
        awaitIdle()

        assertEquals(0, mine.blinkRate, "the declared caret is left at the rate it had")
        assertEquals(BLINK_RATE, field.caret.blinkRate, "the caret given the rate while it stood keeps it")
    }

    @Test
    fun aLookAndFeelChangeLeavesTheFieldCarryingACaret() = runComposeSwingTest {
        setContent {
            SwingNode(factory = { JTextField(TEXT) }, modifier = SwingModifier.caretBlinkRate(BLINK_RATE))
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installed = field.caret

        // A look and feel takes its own caret off the field before putting the next one on, so the field
        // carries none for the span of the swap and the rate has nothing to be written to until it ends.
        // It gives the caret it installs the rate its own defaults name, and reports the look and feel it
        // took only once that is done.
        SwingUtilities.updateComponentTreeUI(field)
        awaitIdle()

        assertNotSame(installed, field.caret, "the look and feel change should hand the field a fresh caret")
        assertEquals(BLINK_RATE, field.caret.blinkRate, "the declared rate should stand over the one installed")
    }

    @Test
    fun aLookAndFeelChangeLeavesTheDeclaredUpdatePolicyStanding() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.caretUpdatePolicy(DefaultCaret.NEVER_UPDATE),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()

        SwingUtilities.updateComponentTreeUI(field)
        awaitIdle()

        assertEquals(
            DefaultCaret.NEVER_UPDATE,
            (field.caret as DefaultCaret).updatePolicy,
            "the declared policy should reach the caret the look and feel installed",
        )
    }

    @Test
    fun aRateReachesACaretThatWasGivenOneBeforeItCarriedAComponent() = runComposeSwingTest {
        // A caret given a rate while it carries no component stashes it and answers with the stash from
        // then on. A look and feel that rates its caret as it installs leaves every caret it builds in
        // that state, so a declaration written afterwards has to reach what the caret answers with, not
        // only its blink timer.
        val stashed = DefaultCaret()
        stashed.blinkRate = SLOW_BLINK_RATE
        assertEquals(SLOW_BLINK_RATE, stashed.blinkRate, "the caret should answer with the rate it stashed")

        setContent {
            SwingNode(
                factory = { JTextField(TEXT).also { it.caret = stashed } },
                modifier = SwingModifier.caretBlinkRate(BLINK_RATE),
            )
        }

        // The rate reaches whichever caret the component carries, so the case says which one before
        // reading the rate off it.
        assertSame(stashed, onNodeOfType<JTextField>().fetch().caret, "the field should still carry that caret")
        assertEquals(BLINK_RATE, stashed.blinkRate, "the declared rate should reach a caret carrying a stash")
    }
}

private const val TEXT = "hello world"
private const val CARET_OFFSET = 4
private const val SELECTION_START = 2
private const val SELECTION_END = 5
private const val BLINK_RATE = 250
private const val SLOW_BLINK_RATE = 500
