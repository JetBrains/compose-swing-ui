package org.jetbrains.compose.swing.modifier.datatransfer

import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.TransferHandler
import kotlin.test.Test
import kotlin.test.assertEquals
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
            "a paste must clear the gate even though its action is outside the accepted mask",
        )
        assertFalse(asked, "the gate must not read the drop action of a transfer that is not a drop")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dropActions")
    fun aDropClearsTheGateExactlyWhenTheMaskAcceptsItsAction(
        acceptedActions: Int,
        dropAction: Int,
        accepted: Boolean,
    ) {
        assertEquals(
            accepted,
            acceptsDropAction(isDrop = true, acceptedActions = acceptedActions, dropAction = { dropAction }),
            "a drop must clear the gate exactly when its action is in the accepted mask",
        )
    }

    companion object {
        @JvmStatic
        fun dropActions(): List<Arguments> = listOf(
            Arguments.of(
                Named.of("single-action mask, its action", TransferHandler.COPY),
                TransferHandler.COPY,
                true,
            ),
            Arguments.of(
                Named.of("single-action mask, another action", TransferHandler.COPY),
                TransferHandler.MOVE,
                false,
            ),
            Arguments.of(
                Named.of("multi-action mask, first member", TransferHandler.COPY_OR_MOVE),
                TransferHandler.COPY,
                true,
            ),
            Arguments.of(
                Named.of("multi-action mask, second member", TransferHandler.COPY_OR_MOVE),
                TransferHandler.MOVE,
                true,
            ),
            Arguments.of(
                Named.of("multi-action mask, non-member", TransferHandler.COPY_OR_MOVE),
                TransferHandler.LINK,
                false,
            ),
        )
    }
}
