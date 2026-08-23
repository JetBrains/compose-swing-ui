package org.jetbrains.compose.swing.node

import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins what [ComponentUpdateBatch.begin] does with a settle an abandoned pass held: a pass that never
 * reaches [ComponentUpdateBatch.end] - a change pass that throws - leaves its held settles behind for
 * [begin] to discard, the same way it already discards that pass's changed containers.
 */
class ComponentUpdateBatchHeldSettleTest {
    @Test
    fun aSettleHeldByAnAbandonedPassDoesNotFireInTheBatchThatFollows() {
        val batch = ComponentUpdateBatch()
        var settled = 0
        val holder = SwingNodeHolder(JPanel()).apply { childSettle = { settled++ } }

        batch.begin()
        batch.holdForChildSettle(holder)
        // The pass is abandoned here, the way a change pass that throws is: it never reaches end().

        batch.begin()
        batch.end {}

        assertEquals(
            0,
            settled,
            "a settle held by a pass that never ended belongs to that pass, not to the batch that follows it",
        )
    }

    @Test
    fun aSettleHeldAfterBeginStillFiresWhenItsOwnBatchEnds() {
        val batch = ComponentUpdateBatch()
        var settled = 0
        val holder = SwingNodeHolder(JPanel()).apply { childSettle = { settled++ } }

        batch.begin()
        batch.holdForChildSettle(holder)
        batch.end {}

        assertEquals(1, settled, "a settle held by the batch that ends it should still run")
    }
}
