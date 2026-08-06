package org.jetbrains.compose.swing.modifier.datatransfer

import javax.swing.TransferHandler
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for [acceptsDropAction], the accepted-actions gate a declared drop target applies
 * before its `canImport` predicate runs. The public `javax.swing.TransferHandler.TransferSupport`
 * exposes no way to construct a drop-mode instance from outside `javax.swing` - its only public
 * constructor always builds a paste-mode instance - so the gate is exercised directly against the
 * `isDrop`/`dropAction` values a real drop or paste would carry.
 */
class DropActionGateTest {
    @Test
    fun aPasteClearsTheGateRegardlessOfTheAcceptedActionsMask() {
        // isDrop false is what a clipboard paste's TransferSupport reports; it carries no drop action
        // to gate on, so the mask never excludes it.
        assertTrue(
            acceptsDropAction(
                isDrop = false,
                acceptedActions = TransferHandler.COPY,
                dropAction = { TransferHandler.MOVE },
            ),
            "a paste must clear the gate even though its action is outside the accepted mask",
        )
    }

    @Test
    fun aPasteIsNeverAskedWhatActionItCarries() {
        // A TransferSupport that is not a drop throws from getDropAction() rather than answer it, so
        // clearing a paste has to happen without the action ever being read.
        var asked = false
        assertTrue(
            acceptsDropAction(
                isDrop = false,
                acceptedActions = TransferHandler.COPY,
                dropAction = {
                    asked = true
                    TransferHandler.MOVE
                },
            ),
        )
        assertFalse(asked, "the gate must not read the drop action of a transfer that is not a drop")
    }

    @Test
    fun aDropWhoseActionIsInTheAcceptedMaskClearsTheGate() {
        assertTrue(
            acceptsDropAction(
                isDrop = true,
                acceptedActions = TransferHandler.COPY,
                dropAction = { TransferHandler.COPY },
            ),
            "a drop whose action is accepted must clear the gate",
        )
    }

    @Test
    fun aDropWhoseActionIsOutsideTheAcceptedMaskIsRefused() {
        assertFalse(
            acceptsDropAction(
                isDrop = true,
                acceptedActions = TransferHandler.COPY,
                dropAction = { TransferHandler.MOVE },
            ),
            "a drop whose action is not accepted must be refused",
        )
    }

    @Test
    fun aDropIsAcceptedWhenItsActionIsAnyMemberOfAMultiActionMask() {
        val copyOrMove = TransferHandler.COPY_OR_MOVE
        assertTrue(
            acceptsDropAction(isDrop = true, acceptedActions = copyOrMove, dropAction = { TransferHandler.COPY }),
            "a drop whose action is one member of a multi-action mask must clear the gate",
        )
        assertTrue(
            acceptsDropAction(isDrop = true, acceptedActions = copyOrMove, dropAction = { TransferHandler.MOVE }),
            "a drop whose action is the other member of a multi-action mask must clear the gate",
        )
        assertFalse(
            acceptsDropAction(isDrop = true, acceptedActions = copyOrMove, dropAction = { TransferHandler.LINK }),
            "a drop whose action is outside every member of a multi-action mask must be refused",
        )
    }
}
