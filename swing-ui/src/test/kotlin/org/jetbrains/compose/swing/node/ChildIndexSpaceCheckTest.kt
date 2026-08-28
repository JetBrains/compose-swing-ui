package org.jetbrains.compose.swing.node

import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for the debug-only child-index-space walk `SwingApplier` schedules under
 * `debugValidateChildIndexSpace`. Each test builds a small [SwingNodeHolder] graph by hand - no
 * [org.jetbrains.compose.swing.node.SwingApplier], composition, or EDT involved - and calls
 * `checkChildIndexSpace()` directly on its outermost holder, standing in for the applier's own root.
 *
 * A holder under test is always nested one level under that outermost holder rather than passed
 * directly, because the walk special-cases the applier's actual root (the one-child slot content mounts
 * into is refused differently than an ordinary host's) - see
 * [aRootDeclaringASingleSlotRefusesASecondChild].
 */
class ChildIndexSpaceCheckTest {
    private val attachment = SlotAttachment { _, _, _ -> {} }

    /** Attaches [child] as [host]'s only real, composed child: on both its children list and its Swing container. */
    private fun attachIndexed(
        host: SwingNodeHolder<*>,
        child: SwingNodeHolder<*>,
    ) {
        (host.component as JPanel).add(child.component)
        host.children += child
    }

    /** Installs [child] into [host]'s named region, consistently on every field the check reads. */
    private fun installSlot(
        host: SwingNodeHolder<*>,
        child: SwingNodeHolder<*>,
        name: String,
    ) {
        child.declaredSlot = DeclaredSlot(attachment, name)
        child.installedSlot = InstalledSlot(attachment, name) {}
        host.children += child
    }

    @Test
    fun aTreeMatchingTheRealSwingStateThroughoutPassesSilently() {
        val root = SwingNodeHolder(JPanel())
        val indexedChild = SwingNodeHolder(JLabel("a"))
        attachIndexed(root, indexedChild)

        val slotsHost = SwingNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("region") }
        attachIndexed(root, slotsHost)
        installSlot(slotsHost, SwingNodeHolder(JLabel("b")), "region")

        root.checkChildIndexSpace()
    }

    @Test
    fun aChildStillAwaitingAttachmentIsReported() {
        val root = SwingNodeHolder(JPanel())
        val child = SwingNodeHolder(JLabel("a")).apply { awaitingAttachment = true }
        root.children += child

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("still awaiting attachment"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aChildHeldByTwoHostsIsReported() {
        val root = SwingNodeHolder(JPanel())
        val hostA = SwingNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        val hostB = SwingNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("b") }
        attachIndexed(root, hostA)
        attachIndexed(root, hostB)

        val shared = SwingNodeHolder(JLabel("shared"))
        installSlot(hostA, shared, "a")
        hostB.children += shared

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("two hosts"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aRegionHostingChildNotInstalledWhereItDeclaresIsReported() {
        val root = SwingNodeHolder(JPanel())
        val host = SwingNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        // Declares a region but was never installed into one: declaredSlot is set, but installedSlot
        // is left null.
        val child = SwingNodeHolder(JLabel("a")).apply { declaredSlot = DeclaredSlot(attachment, "a") }
        host.children += child

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("is not installed there"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun twoChildrenInstalledInOneSlotsRegionAreReported() {
        val root = SwingNodeHolder(JPanel())
        val host = SwingNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        installSlot(host, SwingNodeHolder(JLabel("1")), "a")
        installSlot(host, SwingNodeHolder(JLabel("2")), "a")

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("holds one component per region"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aRootDeclaringASingleSlotRefusesASecondChild() {
        val root = SwingNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("content") }
        installSlot(root, SwingNodeHolder(JLabel("1")), "content")
        installSlot(root, SwingNodeHolder(JLabel("2")), "content")

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("emits two"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aComposedChildMissingFromTheRealContainerIsReported() {
        val root = SwingNodeHolder(JPanel())
        val host = SwingNodeHolder(JPanel())
        attachIndexed(root, host)

        // In the applier's own children bookkeeping, but never actually added to the real JPanel.
        host.children += SwingNodeHolder(JLabel("ghost"))

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("does not hold"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun composedChildrenOutOfCompositionOrderInTheRealContainerAreNotReported() {
        val root = SwingNodeHolder(JPanel())
        val host = SwingNodeHolder(JPanel())
        attachIndexed(root, host)

        // Composed in the order first, second, but attached to the real JLayeredPane in the reverse
        // order - what happens when two composed siblings sit on different layers, which a JLayeredPane
        // sorts its real children by rather than by composition order.
        val first = SwingNodeHolder(JLabel("first"))
        val second = SwingNodeHolder(JLabel("second"))
        (host.component as JPanel).add(second.component)
        (host.component as JPanel).add(first.component)
        host.children += first
        host.children += second

        root.checkChildIndexSpace()
    }

    @Test
    fun aLookAndFeelDecorationAmongTheRealChildrenIsNotReported() {
        val root = SwingNodeHolder(JPanel())
        val host = SwingNodeHolder(JPanel())
        attachIndexed(root, host)

        // A composed child, plus a real Swing child no composable declared - standing in for what a
        // look-and-feel delegate gives a widget of its own (JComboBox's arrow button, JTree's
        // CellRendererPane), which SwingNodeHolder.children never hears about.
        val composed = SwingNodeHolder(JLabel("composed"))
        (host.component as JPanel).add(JPanel())
        (host.component as JPanel).add(composed.component)
        host.children += composed

        root.checkChildIndexSpace()
    }

    @Test
    fun aComposedChildReparentedIntoAnotherContainerIsNotReported() {
        val root = SwingNodeHolder(JPanel())
        val host = SwingNodeHolder(JPanel())
        attachIndexed(root, host)

        // Standing in for a floating JToolBar: its own look-and-feel delegate has taken the component
        // out of the host the composition put it in and reparented it elsewhere - a window the
        // look-and-feel opens while the bar floats, say - and the applier is right to go on holding it
        // here through that.
        val elsewhere = JPanel()
        val reparented = SwingNodeHolder(JLabel("reparented"))
        elsewhere.add(reparented.component)
        host.children += reparented

        root.checkChildIndexSpace()
    }

    @Test
    fun aDeactivatedIndexedChildIsSkipped() {
        val root = SwingNodeHolder(JPanel())
        val host = SwingNodeHolder(JPanel())
        attachIndexed(root, host)

        // onDeactivate already detached this child's component from the real JPanel; it still stands in
        // host.children only because nothing has removed it from the composition for good yet.
        host.children += SwingNodeHolder(JLabel("parked")).apply { deactivated = true }

        root.checkChildIndexSpace()
    }

    @Test
    fun aDeactivatedSlotsChildIsSkipped() {
        val root = SwingNodeHolder(JPanel())
        val host = SwingNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        // onDeactivate already released this child's region (installedSlot is back to null) while it
        // still declares one; it stands in host.children only until the composition removes it for good.
        val parked =
            SwingNodeHolder(JLabel("parked")).apply {
                declaredSlot = DeclaredSlot(attachment, "a")
                deactivated = true
            }
        host.children += parked

        root.checkChildIndexSpace()
    }
}
