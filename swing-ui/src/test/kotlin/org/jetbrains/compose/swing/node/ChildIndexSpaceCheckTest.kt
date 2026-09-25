package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.modifier.layout.RawParentProtocol
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for the debug-only child-index-space walk `SwingApplier` schedules for a composition whose
 * owner names [SwingCompositionDiagnostics]. Each test builds a small [SwingNodeHolder] graph by hand - no
 * [org.jetbrains.compose.swing.node.SwingApplier], composition, or EDT involved - and calls
 * `checkChildIndexSpace()` directly on its outermost holder, standing in for the applier's own root.
 */
class ChildIndexSpaceCheckTest {
    private val attachment = SlotAttachment { _, _, _ -> {} }

    /** Attaches [child] as [host]'s only real, composed child: on both its children list and its Swing container. */
    private fun attachIndexed(
        host: SwingNodeHolder<*>,
        child: SwingNodeHolder<*>,
    ) {
        (host.component as JPanel).add(child.component)
        child.installation = Installation.Indexed
        host.children += child
    }

    /** Installs [child] into [host]'s named region, consistently on every field the check reads. */
    private fun installSlot(
        host: SwingNodeHolder<*>,
        child: SwingNodeHolder<*>,
        name: String,
    ) {
        child.declaredSlot = DeclaredSlot(RawParentProtocol, attachment, name)
        child.installation = Installation.Region(name) {}
        host.children += child
    }

    @Test
    fun aTreeMatchingTheRealSwingStateThroughoutPassesSilently() {
        val root = CreatedNodeHolder(JPanel())
        val indexedChild = CreatedNodeHolder(JLabel("a"))
        attachIndexed(root, indexedChild)

        val slotsHost = CreatedNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("region") }
        attachIndexed(root, slotsHost)
        installSlot(slotsHost, CreatedNodeHolder(JLabel("b")), "region")

        root.checkChildIndexSpace()
    }

    @Test
    fun aChildStillAwaitingAttachmentIsReported() {
        val root = CreatedNodeHolder(JPanel())
        val child = CreatedNodeHolder(JLabel("a")).apply { awaitingAttachment = true }
        root.children += child

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("still awaiting attachment"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aChildHeldByTwoHostsIsReported() {
        val root = CreatedNodeHolder(JPanel())
        val hostA = CreatedNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        val hostB = CreatedNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("b") }
        attachIndexed(root, hostA)
        attachIndexed(root, hostB)

        val shared = CreatedNodeHolder(JLabel("shared"))
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
        val root = CreatedNodeHolder(JPanel())
        val host = CreatedNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        // Declares a region but was never installed into one: declaredSlot is set, but installation
        // is left null.
        val child =
            CreatedNodeHolder(
                JLabel("a"),
            ).apply { declaredSlot = DeclaredSlot(RawParentProtocol, attachment, "a") }
        host.children += child

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("is not installed there"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun twoChildrenInstalledInOneSlotsRegionAreReported() {
        val root = CreatedNodeHolder(JPanel())
        val host = CreatedNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        installSlot(host, CreatedNodeHolder(JLabel("1")), "a")
        installSlot(host, CreatedNodeHolder(JLabel("2")), "a")

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("holds one component per region"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aHostWithContentRefusesASecondUnnamedChild() {
        val root = CreatedNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots(content = attachment) }
        for (text in listOf("1", "2")) {
            root.children += CreatedNodeHolder(JLabel(text)).apply { installation = Installation.Region(null) {} }
        }

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("shows one unnamed child as its content"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun aComposedChildMissingFromTheRealContainerIsReported() {
        val root = CreatedNodeHolder(JPanel())
        val host = CreatedNodeHolder(JPanel())
        attachIndexed(root, host)

        // In the applier's own children bookkeeping, but never actually added to the real JPanel.
        host.children += CreatedNodeHolder(JLabel("ghost")).apply { installation = Installation.Indexed }

        val failure = assertFailsWith<IllegalStateException> { root.checkChildIndexSpace() }
        assertTrue(
            failure.message.orEmpty().contains("does not hold"),
            "the failure should say why: ${failure.message}",
        )
    }

    @Test
    fun composedChildrenOutOfCompositionOrderInTheRealContainerAreNotReported() {
        val root = CreatedNodeHolder(JPanel())
        val host = CreatedNodeHolder(JPanel())
        attachIndexed(root, host)

        // Composed in the order first, second, but attached to the real JLayeredPane in the reverse
        // order - what happens when two composed siblings sit on different layers, which a JLayeredPane
        // sorts its real children by rather than by composition order.
        val first = CreatedNodeHolder(JLabel("first"))
        val second = CreatedNodeHolder(JLabel("second"))
        (host.component as JPanel).add(second.component)
        (host.component as JPanel).add(first.component)
        host.children += first
        host.children += second

        root.checkChildIndexSpace()
    }

    @Test
    fun aLookAndFeelDecorationAmongTheRealChildrenIsNotReported() {
        val root = CreatedNodeHolder(JPanel())
        val host = CreatedNodeHolder(JPanel())
        attachIndexed(root, host)

        // A composed child, plus a real Swing child no composable declared - standing in for what a
        // look-and-feel delegate gives a widget of its own (JComboBox's arrow button, JTree's
        // CellRendererPane), which SwingNodeHolder.children never hears about.
        val composed = CreatedNodeHolder(JLabel("composed"))
        (host.component as JPanel).add(JPanel())
        (host.component as JPanel).add(composed.component)
        host.children += composed

        root.checkChildIndexSpace()
    }

    @Test
    fun aComposedChildReparentedIntoAnotherContainerIsNotReported() {
        val root = CreatedNodeHolder(JPanel())
        val host = CreatedNodeHolder(JPanel())
        attachIndexed(root, host)

        // Standing in for a floating JToolBar: its own look-and-feel delegate has taken the component
        // out of the host the composition put it in and reparented it elsewhere - a window the
        // look-and-feel opens while the bar floats, say - and the applier is right to go on holding it
        // here through that.
        val elsewhere = JPanel()
        val reparented = CreatedNodeHolder(JLabel("reparented"))
        elsewhere.add(reparented.component)
        host.children += reparented

        root.checkChildIndexSpace()
    }

    @Test
    fun aDeactivatedIndexedChildIsSkipped() {
        val root = CreatedNodeHolder(JPanel())
        val host = CreatedNodeHolder(JPanel())
        attachIndexed(root, host)

        // onDeactivate already detached this child's component from the real JPanel; it still stands in
        // host.children only because nothing has removed it from the composition for good yet.
        host.children += CreatedNodeHolder(JLabel("parked")).apply { deactivated = true }

        root.checkChildIndexSpace()
    }

    @Test
    fun aDeactivatedSlotsChildIsSkipped() {
        val root = CreatedNodeHolder(JPanel())
        val host = CreatedNodeHolder(JPanel()).apply { childPlacement = ChildPlacement.Slots("a") }
        attachIndexed(root, host)

        // onDeactivate already released this child's region (installation is back to null) while it
        // still declares one; it stands in host.children only until the composition removes it for good.
        val parked =
            CreatedNodeHolder(JLabel("parked")).apply {
                declaredSlot = DeclaredSlot(RawParentProtocol, attachment, "a")
                deactivated = true
            }
        host.children += parked

        root.checkChildIndexSpace()
    }
}
