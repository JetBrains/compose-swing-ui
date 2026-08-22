package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.reportCallerFailure

/**
 * A reentrancy guard marking the wrapper's own writes to its widget, so a listener can tell them from
 * the user's.
 *
 * Runs on the event dispatch thread.
 */
internal class AppliedWrite {
    /**
     * Count of nested [write]s in flight. A widget raises its event from inside the write that provokes
     * it, before that write returns, so an event arriving while this is above zero is the wrapper's own
     * doing. Counting, rather than a flag, keeps a write nested inside another as one stretch instead of
     * two.
     */
    private var writeDepth: Int = 0

    /**
     * Whether a [write] of this wrapper's own is in flight. A listener that needs to tell the user's
     * changes from the wrapper's reads this directly, instead of comparing against a mirrored value.
     */
    val isWriting: Boolean get() = writeDepth > 0

    /**
     * Runs [block] as the wrapper's own write to its widget, so the events it raises are recognizable as
     * such rather than as something the user did.
     *
     * Use this even for a write that cannot leave the widget holding the declaration - one narrower than
     * it, or a structural change with a side effect on the property a declaration governs. [isWriting]
     * still marks it as the wrapper's, regardless of what the widget ends up holding. A write that throws
     * still lowers the count, so the next write is not mistaken for a nested one.
     *
     * A widget notifies its listeners from inside the write that provokes them, so a listener the caller
     * attached runs before [block] returns. Its failure is caught here instead of carried outward,
     * because the write is driven by a composition applying its changes, and an uncaught failure there
     * would end the composition for good. A caught failure is reported the way Swing reports one raised
     * on its own event pump, and the pass finishes.
     *
     * Every throwable is caught: what a listener throws is the caller's choice, and naming only some
     * types would leave the rest free to end the composition.
     */
    @Suppress("TooGenericExceptionCaught")
    fun write(block: () -> Unit) {
        writeDepth++
        try {
            block()
        } catch (failure: Throwable) {
            reportCallerFailure(failure)
        } finally {
            writeDepth--
        }
    }
}
