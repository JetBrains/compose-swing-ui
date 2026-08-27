package org.jetbrains.compose.swing.node

import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * When an open popup is packed against a detach the runtime dispatches after a change pass:
 * [MenuApplier.onEndChanges] packs synchronously, but a parked node's component leaves in
 * [SwingNodeHolder.onDeactivate], which the runtime dispatches after that call - so each pass also
 * schedules a pack for the next turn of the event queue, the first turn that sees the detach.
 *
 * The popup reports itself showing, so the pack walk reaches it without a window system realizing it,
 * and records its item count at each pack - the counts say which state each pack saw. The passes run on
 * the event dispatch thread, the way the runtime applies them, so the deferred pack can only run once
 * the turn that scheduled it - and the deactivation dispatched on it - has ended.
 */
class MenuApplierDeferredPackTest {
    /** A popup the pack walk takes for one on screen, recording its item count at each pack. */
    private class RecordingPopup : JPopupMenu() {
        val packedCounts = mutableListOf<Int>()

        override fun isVisible(): Boolean = true

        override fun pack() {
            packedCounts += componentCount
        }
    }

    /**
     * A menu whose drop-down is a [RecordingPopup]. The popup is built in the getter rather than in the
     * constructor, mirroring `JMenu.ensurePopupMenuCreated`, so a getter call from the superclass
     * constructor cannot see a null field.
     */
    private class RecordingMenu : JMenu("More") {
        private var recording: RecordingPopup? = null

        override fun getPopupMenu(): JPopupMenu = recording ?: RecordingPopup().also { recording = it }
    }

    /** Runs one change pass on the root: positions on it, applies [block], and ends the pass. */
    private fun MenuApplier.pass(block: MenuApplier.() -> Unit) {
        onBeginChanges()
        down(root)
        block()
        up()
        onEndChanges()
    }

    @Test
    fun aDetachDispatchedAfterThePassIsCoveredByTheDeferredPack() {
        val popup = RecordingPopup()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))
        val first = SwingNodeHolder(JMenuItem("first"))

        SwingUtilities.invokeAndWait {
            applier.pass {
                insertBottomUp(0, first)
                insertBottomUp(1, SwingNodeHolder(JMenuItem("second")))
            }
            first.onDeactivate()
        }
        // Drain the queue: the pack the pass scheduled runs before this returns.
        SwingUtilities.invokeAndWait {}

        assertEquals(
            listOf(2, 1),
            popup.packedCounts,
            "the pass packs the two items it attached, and the deferred pack sees the one the detach left",
        )
    }

    @Test
    fun passesAppliedInOneTurnShareOneDeferredPack() {
        val popup = RecordingPopup()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))

        SwingUtilities.invokeAndWait {
            applier.pass { insertBottomUp(0, SwingNodeHolder(JMenuItem("first"))) }
            applier.pass { insertBottomUp(1, SwingNodeHolder(JMenuItem("second"))) }
        }
        SwingUtilities.invokeAndWait {}

        assertEquals(
            listOf(1, 2, 2),
            popup.packedCounts,
            "each pass packs what it attached, and both share the one pack on the turn that follows",
        )
    }

    @Test
    fun aPassThatChangesNoContainerStillPacksTheShowingPopup() {
        val popup = RecordingPopup()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))

        SwingUtilities.invokeAndWait {
            applier.pass { insertBottomUp(0, SwingNodeHolder(JMenuItem("only"))) }
            // The shape a pass takes when only an update block ran: no menu container was touched.
            applier.pass { }
        }
        SwingUtilities.invokeAndWait {}

        assertEquals(
            listOf(1, 1, 1),
            popup.packedCounts,
            "a pass that changes no container still packs the open popup, because an update block that " +
                "resizes an item touches no container",
        )
    }

    @Test
    fun thePackWalkReachesASubmenusOwnPopup() {
        val popup = RecordingPopup()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))
        val submenu = RecordingMenu()
        val menuHolder = SwingNodeHolder(submenu)

        SwingUtilities.invokeAndWait {
            applier.pass { insertBottomUp(0, menuHolder) }
            applier.pass {
                down(menuHolder)
                insertBottomUp(0, SwingNodeHolder(JMenuItem("nested")))
                up()
            }
        }
        SwingUtilities.invokeAndWait {}

        assertEquals(
            listOf(0, 1, 1),
            (submenu.popupMenu as RecordingPopup).packedCounts,
            "the pack walk must reach a submenu's own popup, which is the only popup a menu bar has",
        )
    }
}
