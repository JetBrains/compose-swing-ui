package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.listener.listener
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Directly exercises the [SwingNodeHolder] lifecycle contract that the components layer depends on,
 * through its public lifecycle hooks ([SwingNodeHolder.onRelease], [SwingNodeHolder.onReuse],
 * [SwingNodeHolder.onDeactivate]) and the real modifier mechanism.
 *
 * The high-severity regression these tests pin down: on release / reuse / deactivation the holder
 * must run every modifier-installed listener's detacher AND drop its cached modifier diff so the
 * next `applyModifier` re-installs a fresh listener from a clean baseline. If the holder detached but
 * left the diff state populated, a recycled slot would see the install-guard still tripped, never
 * re-attach, and the reused component would go silently dead.
 *
 * Every assertion is on *observable* behavior - how many times a listener attaches/detaches, that a
 * release/uninstall block runs exactly once across repeated calls, and that a fresh listener
 * re-installs after a reuse cycle - never on the holder's internal diff/handle fields. The
 * attach/detach counters are the observable proxy for the diff state being cleared: if the diff were
 * left populated, the post-reuse `applyModifierDiff` would not re-attach and the attach counter would
 * not advance.
 */
class SwingNodeHolderLifecycleTest {
    /**
     * Builds a single-listener modifier that records attach/detach counts. The listener instance is a
     * stable marker shared across the builder's calls, so re-applying it while attached is a by-identity
     * no-op (the install-guard holds); attach increments on install, detach on removal. The listener
     * never fires in these lifecycle tests, only attaches/detaches.
     */
    private val markerInstance = Any()

    private fun listenerModifier(
        attachCounter: IntArray,
        detachCounter: IntArray,
    ): SwingModifier = SwingModifier.listener<JButton, Any>(
        instance = markerInstance,
        attach = { _, _ -> attachCounter[0]++ },
        detach = { _, _ -> detachCounter[0]++ },
    )

    @Test
    fun onRelease_runsReleaseBlockOnceAndDetachesListeners() {
        val holder = SwingNodeHolder(JButton("b"))
        val attach = IntArray(1)
        val detach = IntArray(1)
        val releaseCount = IntArray(1)
        holder.releaseBlock = { releaseCount[0]++ }

        holder.applyModifierDiff(listenerModifier(attach, detach))
        assertEquals(1, attach[0], "applying the modifier must attach exactly one listener")

        holder.onRelease()

        assertEquals(1, detach[0], "detacher must run once on release")
        assertEquals(1, releaseCount[0], "release block must run once on release")

        // A second release (defensive: the runtime should not call twice, but nothing must fire
        // again - observable proof the release block and detacher were dropped).
        holder.onRelease()
        assertEquals(1, detach[0], "a second release must not run the detacher again")
        assertEquals(1, releaseCount[0], "a second release must not run the release block again")
    }

    @Test
    fun onReuse_detachesAndClearsStateSoNextApplyReattaches() {
        val holder = SwingNodeHolder(JButton("b"))
        val attach = IntArray(1)
        val detach = IntArray(1)

        // First composition: applying the modifier attaches exactly one listener.
        holder.applyModifierDiff(listenerModifier(attach, detach))
        assertEquals(1, attach[0], "the first apply must attach exactly one listener")

        // Re-applying the same listener position while attached is a no-op (install-guard holds):
        // still exactly one listener.
        holder.applyModifierDiff(listenerModifier(attach, detach))
        assertEquals(1, attach[0], "re-applying the same listener while attached must not re-attach")

        // Slot reuse.
        holder.onReuse()
        assertEquals(1, detach[0], "reuse must run the detacher")

        // Next apply after reuse MUST re-attach a fresh listener (the core regression check: this can
        // only advance if reuse cleared the cached diff so the install-guard no longer holds).
        holder.applyModifierDiff(listenerModifier(attach, detach))
        assertEquals(2, attach[0], "a reused holder must re-attach exactly one fresh listener")
    }

    @Test
    fun reuseKeepsTheComponentInItsHostSlotAndParkingGivesItUp() {
        val holder = SwingNodeHolder(JButton("b"))
        val released = IntArray(1)
        // A node installed in a host slot: the applier captured an uninstall handle when it installed
        // the component through the slot's dedicated setter.
        holder.installedSlot = InstalledSlot(SlotAttachment { _, _, _ -> {} }, "region") { released[0]++ }

        // Recycling changes what drives the component, never where it lives: the new content takes over
        // the slot the old one filled, with no pass in between at which the slot stands empty.
        holder.onReuse()
        assertEquals(0, released[0], "reuse must leave the component in its host slot")

        // Parking gives the slot up: a slot holds one child, and one held by content nothing drives could
        // not be given to a replacement. A parked node is never driven again, so nothing here restores it.
        holder.onDeactivate()
        assertEquals(1, released[0], "parking must release the host slot")

        // The slot is released once per install: a parked node has already given it up.
        holder.onRelease()
        assertEquals(1, released[0], "release must not release a slot parking already released")
    }

    @Test
    fun onRelease_releasesAStillInstalledHostSlotOnce() {
        val holder = SwingNodeHolder(JButton("b"))
        val released = IntArray(1)
        holder.installedSlot = InstalledSlot(SlotAttachment { _, _, _ -> {} }, "region") { released[0]++ }

        holder.onRelease()
        assertEquals(1, released[0], "release must release a still-installed host slot")

        // A second release must not re-run the uninstall handle.
        holder.onRelease()
        assertEquals(1, released[0], "the slot must not be released twice")
    }

    @Test
    fun onDeactivate_detachesAndClearsStateSoLaterActivationReattaches() {
        val holder = SwingNodeHolder(JButton("b"))
        val attach = IntArray(1)
        val detach = IntArray(1)

        holder.applyModifierDiff(listenerModifier(attach, detach))
        assertEquals(1, attach[0], "applying the modifier must attach exactly one listener")

        // Deactivation (movableContent parked the node).
        holder.onDeactivate()
        assertEquals(1, detach[0], "deactivate must run the detacher")

        // A later apply on the same holder must find its diff state clean, whatever calls it: the
        // observable proof that onDeactivate cleared it rather than leaving the install-guard tripped.
        holder.applyModifierDiff(listenerModifier(attach, detach))
        assertEquals(2, attach[0], "a later apply on a deactivated holder must re-attach exactly one fresh listener")
    }

    @Test
    fun repeatedReuseCyclesKeepListenerCountBalanced() {
        val holder = SwingNodeHolder(JButton("b"))
        val attach = IntArray(1)
        val detach = IntArray(1)

        repeat(4) {
            holder.applyModifierDiff(listenerModifier(attach, detach))
            holder.onReuse()
        }

        // Each cycle attaches once and detaches once: no leak, no double-attach.
        assertEquals(4, attach[0], "each of the four cycles must attach exactly once")
        assertEquals(4, detach[0], "each of the four cycles must detach exactly once")
    }

    @Test
    fun onReuse_withoutListenerIsHarmlessAndLaterApplyStillAttaches() {
        val holder = SwingNodeHolder(JButton("b"))
        // No modifier applied yet - reuse must be a harmless no-op.
        holder.onReuse()

        // Subsequent apply still attaches exactly one listener and detaches none.
        val attach = IntArray(1)
        val detach = IntArray(1)
        holder.applyModifierDiff(listenerModifier(attach, detach))
        assertEquals(1, attach[0], "apply after a no-op reuse must still attach exactly one listener")
        assertEquals(0, detach[0], "apply after a no-op reuse must not detach anything")
    }
}
