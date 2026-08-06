package org.jetbrains.compose.swing.core

import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Low-level unit tests that drive [SwingApplier] directly over a root [JPanel], with no Compose
 * runtime, recomposer, or clock involved. The applier's mutation contract (insert / remove / move /
 * clear / onEndChanges) is exercised in isolation so regressions in the AWT-tree manipulation math
 * surface here rather than in higher-level integration tests.
 *
 * Mutations and assertions that observe `revalidate()`/`repaint()` call counts run on the EDT (via
 * [onEdt]), matching production where the applier always runs on the EDT. On the EDT
 * `JComponent.revalidate()` is synchronous (it invalidates and registers the component immediately),
 * so every counted call is exact and deterministic; off the EDT it would defer via `invokeLater`,
 * racing the count against the assertion.
 */
class SwingApplierTest {
    /**
     * Runs [block] on the AWT event dispatch thread and surfaces any failure on the calling thread.
     *
     * If already on the EDT, [block] runs inline; otherwise it is dispatched with
     * [EventQueue.invokeAndWait] and any thrown failure is rethrown here so assertions inside [block]
     * fail the test as usual.
     */
    private fun onEdt(block: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            block()
            return
        }
        var failure: Throwable? = null
        EventQueue.invokeAndWait { runCatching(block).onFailure { failure = it } }
        failure?.let { throw it }
    }

    /**
     * A JPanel that counts how many times [revalidate] and `repaint` are invoked, for the
     * onEndChanges assertions. Every `Component.repaint()` overload funnels through the five-argument
     * `repaint(long, int, int, int, int)`, so counting there captures the applier's bare
     * `container.repaint()` call regardless of how it is dispatched.
     */
    private class CountingPanel : JPanel() {
        var revalidateCount: Int = 0
            private set
        var repaintCount: Int = 0
            private set

        override fun revalidate() {
            revalidateCount++
            super.revalidate()
        }

        override fun repaint(
            tm: Long,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
            repaintCount++
            super.repaint(tm, x, y, width, height)
        }
    }

    /**
     * Positions the applier's `current` on [holder], runs [block] against the applier,
     * and returns to the root. [insertBottomUp] and friends always operate on `current`, so tests
     * must navigate there first via [SwingApplier.down].
     */
    private fun SwingApplier.onContainer(
        holder: SwingNodeHolder<*>,
        block: SwingApplier.() -> Unit,
    ) {
        down(holder)
        block()
        up()
    }

    private fun holder(component: Component): SwingNodeHolder<*> = SwingNodeHolder(component)

    /** Observers created for the appliers under test, disposed in [disposeObservers]. */
    private val observers = mutableListOf<SnapshotStateObserver>()

    /**
     * Builds a [SwingApplier] over [root] with a snapshot observer this test owns and disposes, so the
     * global apply-observer registration the applier starts is torn down at test end rather than
     * leaked (the production path disposes it with the composition mount).
     */
    private fun applierFor(root: Container): SwingApplier {
        val observer = SnapshotStateObserver { it() }.apply { start() }
        observers += observer
        return SwingApplier(root, observer)
    }

    @AfterTest
    fun disposeObservers() {
        observers.forEach { it.stop() }
        observers.clear()
    }

    private fun namedButton(name: String): JButton = JButton(name).apply { this.name = name }

    private fun childNames(container: Container): List<String> = container.components.map { it.name }

    @Test
    fun insertBottomUp_addsChildToCurrentContainer() {
        val root = JPanel()
        val applier = applierFor(root)
        val child = namedButton("a")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, holder(child))
        }
        applier.onEndChanges()

        assertEquals(1, root.componentCount, "the container should hold exactly the inserted child")
        assertSame(child, root.getComponent(0), "the inserted child instance should be at index 0")
    }

    @Test
    fun insertBottomUp_insertsAtRequestedIndexForUnconstrainedChildren() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, holder(namedButton("a")))
            insertBottomUp(1, holder(namedButton("b")))
            // Insert "c" between a and b.
            insertBottomUp(1, holder(namedButton("c")))
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "c", "b"), childNames(root))
    }

    @Test
    fun remove_removesContiguousRunStartingAtIndex() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            listOf("a", "b", "c", "d").forEachIndexed { i, n ->
                insertBottomUp(i, holder(namedButton(n)))
            }
        }
        applier.onEndChanges()

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            // Remove "b" and "c".
            remove(1, 2)
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "d"), childNames(root))
    }

    @Test
    fun move_forwardReordersCorrectly() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            listOf("a", "b", "c", "d").forEachIndexed { i, n ->
                insertBottomUp(i, holder(namedButton(n)))
            }
        }
        applier.onEndChanges()

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            // Move single item "a" (index 0) to index 3 (after the run removal, mirrors Compose math).
            move(0, 3, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("b", "c", "a", "d"), childNames(root))
    }

    @Test
    fun move_backwardReordersCorrectly() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            listOf("a", "b", "c", "d").forEachIndexed { i, n ->
                insertBottomUp(i, holder(namedButton(n)))
            }
        }
        applier.onEndChanges()

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            // Move "d" (index 3) to index 1.
            move(3, 1, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "d", "b", "c"), childNames(root))
    }

    @Test
    fun move_multiCountForwardReordersWholeRun() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            listOf("a", "b", "c", "d", "e").forEachIndexed { i, n ->
                insertBottomUp(i, holder(namedButton(n)))
            }
        }
        applier.onEndChanges()

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            // Move the run [a, b] (indices 0..1, count 2) to index 4.
            move(0, 4, 2)
        }
        applier.onEndChanges()

        assertEquals(listOf("c", "d", "a", "b", "e"), childNames(root))
    }

    @Test
    fun move_multiCountBackwardReordersWholeRun() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            listOf("a", "b", "c", "d", "e").forEachIndexed { i, n ->
                insertBottomUp(i, holder(namedButton(n)))
            }
        }
        applier.onEndChanges()

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            // Move the run [d, e] (indices 3..4, count 2) to index 0.
            move(3, 0, 2)
        }
        applier.onEndChanges()

        assertEquals(listOf("d", "e", "a", "b", "c"), childNames(root))
    }

    @Test
    fun move_sameSourceAndTargetIsNoOp() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            listOf("a", "b", "c").forEachIndexed { i, n ->
                insertBottomUp(i, holder(namedButton(n)))
            }
        }
        applier.onEndChanges()

        val before = root.components.toList()
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            move(1, 1, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "b", "c"), childNames(root), "a no-op move should leave the child order unchanged")
        // Identity preserved (no remove/re-add churn for a no-op).
        before.forEachIndexed {
            i,
            c,
            ->
            assertSame(c, root.getComponent(i), "child $i should keep its identity after a no-op move")
        }
    }

    @Test
    fun onClear_emptiesRootContainer() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            listOf("a", "b", "c").forEachIndexed { i, n ->
                insertBottomUp(i, holder(namedButton(n)))
            }
        }
        applier.onEndChanges()
        assertEquals(3, root.componentCount, "the container should hold all three children before clearing")

        // AbstractApplier.clear() invokes onClear() and resets the navigation stack to root.
        applier.clear()

        assertEquals(0, root.componentCount, "clear should empty the root container")
    }

    @Test
    fun onEndChanges_revalidatesMutatedContainerOnce() = onEdt {
        val root = CountingPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, holder(namedButton("a")))
            insertBottomUp(1, holder(namedButton("b")))
        }
        val countBeforeEnd = root.revalidateCount
        applier.onEndChanges()

        // Exactly one revalidate triggered by onEndChanges for the single mutated container,
        // regardless of how many child mutations happened during the pass.
        assertEquals(countBeforeEnd + 1, root.revalidateCount)
    }

    @Test
    fun onEndChanges_doesNotRevalidateWhenNoContainerMutated() = onEdt {
        val root = CountingPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        // No insert/remove/move at all this pass.
        val before = root.revalidateCount
        applier.onEndChanges()

        assertEquals(before, root.revalidateCount)
    }

    @Test
    fun onEndChanges_repaintsParentRegionAfterRemovingChild() = onEdt {
        val root = CountingPanel()
        val applier = applierFor(root)

        // Seed the container with two children in their own pass.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, holder(namedButton("a")))
            insertBottomUp(1, holder(namedButton("b")))
        }
        applier.onEndChanges()

        val repaintsBeforeRemoval = root.repaintCount

        // Removing a child vacates its region; AWT's Container.remove only invalidates and never
        // repaints those pixels, so the applier must repaint the parent once in onEndChanges.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            remove(0, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("b"), childNames(root), "the removed child should be gone")
        assertEquals(
            repaintsBeforeRemoval + 1,
            root.repaintCount,
            "the parent region should be repainted once after a child is removed",
        )
    }

    @Test
    fun mutatingNestedContainerAppliesChildAndRevalidatesThatContainerNotRoot() = onEdt {
        val root = CountingPanel()
        val applier = applierFor(root)
        // The nested container counts its own revalidate() calls so we can prove the applier
        // targeted and revalidated the inner panel, not the root.
        val childPanel = CountingPanel().apply { name = "child" }

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, holder(childPanel))
        }
        applier.onEndChanges()

        val rootRevalidatesAfterFirstPass = root.revalidateCount
        val childRevalidatesAfterFirstPass = childPanel.revalidateCount

        // Second pass: descend into the child container and add a leaf there.
        applier.onBeginChanges()
        applier.onContainer(holder(childPanel)) {
            insertBottomUp(0, holder(JLabel("inner")))
        }
        applier.onEndChanges()

        // The label landed in the child, not the root.
        assertEquals(1, childPanel.componentCount, "the leaf should land in the child container")
        assertEquals(1, root.componentCount, "the root should still hold only the child panel")
        assertSame(childPanel, root.getComponent(0), "the child panel should remain the root's only child")
        // onEndChanges revalidated the mutated child once, and did NOT revalidate the untouched root.
        assertEquals(
            childRevalidatesAfterFirstPass + 1,
            childPanel.revalidateCount,
            "the mutated child container should be revalidated once",
        )
        assertEquals(
            rootRevalidatesAfterFirstPass,
            root.revalidateCount,
            "the untouched root should not be revalidated",
        )
    }

    /** A holder whose node fills a host slot, installing nothing and releasing nothing. */
    private fun slotHolder(component: Component): SwingNodeHolder<*> =
        holder(component).apply { slotAttachment = SlotAttachment { _, _, _ -> {} } }

    @Test
    fun aSlotChildIsRefusedWhereChildrenAreAlreadyAddedByIndex() = onEdt {
        val applier = applierFor(JPanel())
        applier.insertBottomUp(0, holder(JLabel("by index")))

        // A node's children are one index space, and the two kinds are addressed through different
        // lists, so a container holding both would have its children reached by the other kind's index.
        val failure = assertFailsWith<IllegalStateException> { applier.insertBottomUp(1, slotHolder(JLabel("slot"))) }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("already holds children added by index"), "the failure should say why: $message")
        assertTrue(message.contains("JPanel"), "the failure should name the host: $message")
    }

    @Test
    fun aChildAddedByIndexIsRefusedWhereSlotChildrenAreAlreadyInstalled() = onEdt {
        val applier = applierFor(JPanel())
        applier.insertBottomUp(0, slotHolder(JLabel("slot")))

        val failure = assertFailsWith<IllegalStateException> { applier.insertBottomUp(1, holder(JLabel("by index"))) }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("already holds slot-attached children"), "the failure should say why: $message")
        assertTrue(message.contains("JPanel"), "the failure should name the host: $message")
    }

    @Test
    fun aHostWhoseSlotChildrenAllLeftTakesChildrenByIndex() = onEdt {
        val root = JPanel()
        val applier = applierFor(root)
        applier.insertBottomUp(0, slotHolder(JLabel("slot")))
        applier.remove(0, 1)

        // The refusal is about children held at the same time, not about what the host once held.
        val label = JLabel("by index")
        applier.insertBottomUp(0, holder(label))

        assertSame(label, root.getComponent(0), "the host should take the child by index")
    }
}
