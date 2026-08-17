package org.jetbrains.compose.swing.node

import java.awt.Component
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
 * directly, because the walk special-cases the applier's actual root (a mount's one-child slot is
 * refused differently than an ordinary host's) - see [aRootDeclaringASingleSlotRefusesASecondChild].
 */
class ChildIndexSpaceCheckTest {
    private fun holder(component: Component): SwingNodeHolder<Component> = SwingNodeHolder(component)

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
        val root = holder(JPanel())
        val indexedChild = holder(JLabel("a"))
        attachIndexed(root, indexedChild)

        val slotsHost = holder(JPanel()).apply { childPlacement = ChildPlacement.Slots("region") }
        attachIndexed(root, slotsHost)
        installSlot(slotsHost, holder(JLabel("b")), "region")

        root.checkChildIndexSpace()
    }

    @Test
    fun aChildStillAwaitingAttachmentIsReported() {
        val root = holder(JPanel())
        val child = holder(JLabel("a")).apply { awaitingAttachment = true }
        root.children += child

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("still awaiting attachment"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aChildHeldByTwoHostsIsReported() {
        val root = holder(JPanel())
        val hostA = holder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        val hostB = holder(JPanel()).apply { childPlacement = ChildPlacement.Slots("b") }
        attachIndexed(root, hostA)
        attachIndexed(root, hostB)

        val shared = holder(JLabel("shared"))
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
        val root = holder(JPanel())
        val host = holder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        // Declares a region but was never installed into one: declaredSlot is set, but installedSlot
        // is left null.
        val child = holder(JLabel("a")).apply { declaredSlot = DeclaredSlot(attachment, "a") }
        host.children += child

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("is not installed there"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun twoChildrenInstalledInOneSlotsRegionAreReported() {
        val root = holder(JPanel())
        val host = holder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        installSlot(host, holder(JLabel("1")), "a")
        installSlot(host, holder(JLabel("2")), "a")

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("holds one component per region"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aRootDeclaringASingleSlotRefusesASecondChild() {
        val root = holder(JPanel()).apply { childPlacement = ChildPlacement.Slots("content") }
        installSlot(root, holder(JLabel("1")), "content")
        installSlot(root, holder(JLabel("2")), "content")

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("emits two"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aComposedChildMissingFromTheRealContainerIsReported() {
        val root = holder(JPanel())
        val host = holder(JPanel())
        attachIndexed(root, host)

        // In the applier's own children bookkeeping, but never actually added to the real JPanel.
        host.children += holder(JLabel("ghost"))

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("does not hold"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun composedChildrenOutOfCompositionOrderInTheRealContainerAreNotReported() {
        val root = holder(JPanel())
        val host = holder(JPanel())
        attachIndexed(root, host)

        // Composed in the order first, second, but attached to the real JLayeredPane in the reverse
        // order - what happens when two composed siblings sit on different layers, which a JLayeredPane
        // sorts its real children by rather than by composition order.
        val first = holder(JLabel("first"))
        val second = holder(JLabel("second"))
        (host.component as JPanel).add(second.component)
        (host.component as JPanel).add(first.component)
        host.children += first
        host.children += second

        root.checkChildIndexSpace()
    }

    @Test
    fun aLookAndFeelDecorationAmongTheRealChildrenIsNotReported() {
        val root = holder(JPanel())
        val host = holder(JPanel())
        attachIndexed(root, host)

        // A composed child, plus a real Swing child no composable declared - standing in for what a
        // look-and-feel delegate gives a widget of its own (JComboBox's arrow button, JTree's
        // CellRendererPane), which SwingNodeHolder.children never hears about.
        val composed = holder(JLabel("composed"))
        (host.component as JPanel).add(JPanel())
        (host.component as JPanel).add(composed.component)
        host.children += composed

        root.checkChildIndexSpace()
    }

    @Test
    fun aComposedChildReparentedIntoAnotherContainerIsNotReported() {
        val root = holder(JPanel())
        val host = holder(JPanel())
        attachIndexed(root, host)

        // Standing in for a floating JToolBar: its own look-and-feel delegate has taken the component
        // out of the host the composition put it in and reparented it elsewhere - a window the
        // look-and-feel opens while the bar floats, say - and the applier is right to go on holding it
        // here through that.
        val elsewhere = JPanel()
        val reparented = holder(JLabel("reparented"))
        elsewhere.add(reparented.component)
        host.children += reparented

        root.checkChildIndexSpace()
    }

    @Test
    fun aDeactivatedIndexedChildIsSkipped() {
        val root = holder(JPanel())
        val host = holder(JPanel())
        attachIndexed(root, host)

        // onDeactivate already detached this child's component from the real JPanel; it still stands in
        // host.children only because nothing has removed it from the composition for good yet.
        host.children += holder(JLabel("parked")).apply { deactivated = true }

        root.checkChildIndexSpace()
    }

    @Test
    fun aDeactivatedSlotsChildIsSkipped() {
        val root = holder(JPanel())
        val host = holder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        // onDeactivate already released this child's region (installedSlot is back to null) while it
        // still declares one; it stands in host.children only until the composition removes it for good.
        val parked =
            holder(JLabel("parked")).apply {
                declaredSlot = DeclaredSlot(attachment, "a")
                deactivated = true
            }
        host.children += parked

        root.checkChildIndexSpace()
    }
}
