package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.runSwingTest
import java.awt.Container
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Low-level unit tests that drive [SwingApplier] directly over a root [JPanel], with no Compose
 * runtime, recomposer, or clock involved. The applier's mutation contract (insert / remove / move /
 * clear / onEndChanges) is exercised in isolation so regressions in the AWT-tree manipulation math
 * surface here rather than in higher-level integration tests.
 *
 * Mutations and assertions that observe `revalidate()`/`repaint()` call counts run on the EDT (via
 * [runSwingTest]), matching production where the applier always runs on the EDT. On the EDT
 * `JComponent.revalidate()` is synchronous (it invalidates and registers the component immediately),
 * so every counted call is exact and deterministic; off the EDT it would defer via `invokeLater`,
 * racing the count against the assertion.
 *
 * A host that holds each child in a region of its own is a placement of its own, and its cases are in
 * [SwingApplierRegionTest].
 */
class SwingApplierTest {
    /**
     * A JPanel that counts how many times [revalidate] is invoked and records the area each `repaint`
     * asks for, for the onEndChanges assertions. Every `Component.repaint()` overload funnels through the
     * five-argument `repaint(long, int, int, int, int)`, so recording there captures every request
     * regardless of how it is made.
     */
    private class CountingPanel : JPanel(null) {
        var revalidateCount: Int = 0
            private set

        val repaints: MutableList<Rectangle> = mutableListOf()

        // False until construction ends, so the requests JPanel's own constructor makes are not recorded.
        private var constructed = false

        init {
            constructed = true
        }

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
            if (constructed) repaints += Rectangle(x, y, width, height)
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

    /**
     * Hands [instance] to the applier the way the runtime hands over a freshly composed node: top-down as
     * the node is created, and bottom-up as its group ends, both naming the composition index it takes.
     * The node's own update runs between the two, which the tests that need it write as [onNode].
     */
    private fun SwingApplier.insertChild(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        insertTopDown(index, instance)
        insertBottomUp(index, instance)
    }

    /** Owners created for the appliers under test, disposed in [disposeOwners]. */
    private val owners = mutableListOf<TestCompositionOwner>()

    /**
     * Builds a [SwingApplier] over [root] with a snapshot observer this test owns and disposes, so the
     * global apply-observer registration the applier starts is torn down at test end rather than
     * leaked (the production path disposes it with the content composition).
     */
    private fun applierFor(root: Container): SwingApplier {
        val owner = TestCompositionOwner()
        owners += owner
        return SwingApplier(SwingNodeHolder(root).attachedTo(owner))
    }

    @AfterTest
    fun disposeOwners() {
        owners.forEach { it.dispose() }
        owners.clear()
    }

    private fun namedButton(name: String): JButton = JButton(name).apply { this.name = name }

    private fun childNames(container: Container): List<String> = container.components.map { it.name }

    @Test
    fun insert_addsChildToCurrentContainer() {
        val root = JPanel()
        val applier = applierFor(root)
        val child = namedButton("a")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertChild(0, SwingNodeHolder(child))
        }
        applier.onEndChanges()

        assertEquals(1, root.componentCount, "the container should hold exactly the inserted child")
        assertSame(child, root.getComponent(0), "the inserted child instance should be at index 0")
    }

    @Test
    fun insert_insertsAtRequestedIndexForUnconstrainedChildren() {
        val root = JPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertChild(0, SwingNodeHolder(namedButton("a")))
            insertChild(1, SwingNodeHolder(namedButton("b")))
            // Insert "c" between a and b.
            insertChild(1, SwingNodeHolder(namedButton("c")))
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
                insertChild(i, SwingNodeHolder(namedButton(n)))
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
                insertChild(i, SwingNodeHolder(namedButton(n)))
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
                insertChild(i, SwingNodeHolder(namedButton(n)))
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
                insertChild(i, SwingNodeHolder(namedButton(n)))
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
                insertChild(i, SwingNodeHolder(namedButton(n)))
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
                insertChild(i, SwingNodeHolder(namedButton(n)))
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
                insertChild(i, SwingNodeHolder(namedButton(n)))
            }
        }
        applier.onEndChanges()
        assertEquals(3, root.componentCount, "the container should hold all three children before clearing")

        // AbstractApplier.clear() invokes onClear() and resets the navigation stack to root.
        applier.clear()

        assertEquals(0, root.componentCount, "clear should empty the root container")
    }

    @Test
    fun onEndChanges_revalidatesMutatedContainerOnce() = runSwingTest {
        val root = CountingPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertChild(0, SwingNodeHolder(namedButton("a")))
            insertChild(1, SwingNodeHolder(namedButton("b")))
        }
        val countBeforeEnd = root.revalidateCount
        applier.onEndChanges()

        // Exactly one revalidate triggered by onEndChanges for the single mutated container,
        // regardless of how many child mutations happened during the pass.
        assertEquals(countBeforeEnd + 1, root.revalidateCount)
    }

    @Test
    fun onEndChanges_doesNotRevalidateWhenNoContainerMutated() = runSwingTest {
        val root = CountingPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        // No insert/remove/move at all this pass.
        val before = root.revalidateCount
        applier.onEndChanges()

        assertEquals(before, root.revalidateCount)
    }

    @Test
    fun onEndChanges_doesNotRevalidateAContainerAnAbandonedPassMarked() = runSwingTest {
        val root = CountingPanel()
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) { insertChild(0, SwingNodeHolder(namedButton("a"))) }
        // A pass that throws unwinds past the call the runtime would have ended it with, so the applier
        // is never told this one ended and the container it marked is never refreshed for it.

        applier.onBeginChanges()
        val before = root.revalidateCount
        applier.onEndChanges()

        // The second pass mutated nothing, so what it refreshes says whether it inherited the first
        // pass's containers.
        assertEquals(before, root.revalidateCount)
    }

    /** Seeds [root], sized 200x100, with three buttons in a row, each 50x20, in a pass of their own. */
    private fun seedRow(
        root: CountingPanel,
        applier: SwingApplier,
    ) {
        root.setSize(200, 100)
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            listOf("a", "b", "c").forEachIndexed { i, n ->
                insertChild(i, SwingNodeHolder(namedButton(n).apply { setBounds(i * 50, 0, 50, 20) }))
            }
        }
        applier.onEndChanges()
        root.repaints.clear()
    }

    @Test
    fun onEndChanges_repaintsOnlyTheAreaARemovedChildLeaves() = runSwingTest {
        val root = CountingPanel()
        val applier = applierFor(root)
        seedRow(root, applier)

        // Container.remove only invalidates, so the area the child leaves is repainted by the applier; the
        // siblings keep their bounds and need nothing.
        applier.onBeginChanges()
        applier.onContainer(applier.root) { remove(1, 1) }
        applier.onEndChanges()

        assertEquals(listOf("a", "c"), childNames(root), "the removed child should be gone")
        assertEquals(
            listOf(Rectangle(50, 0, 50, 20)),
            root.repaints,
            "only the area the removed child leaves should be repainted",
        )
    }

    @Test
    fun onEndChanges_repaintsOnlyTheAreaOfAMovedChild() = runSwingTest {
        val root = CountingPanel()
        val applier = applierFor(root)
        seedRow(root, applier)

        applier.onBeginChanges()
        applier.onContainer(applier.root) { move(0, 3, 1) }
        applier.onEndChanges()

        assertEquals(listOf("b", "c", "a"), childNames(root), "the child should move to the end")
        assertEquals(
            listOf(Rectangle(0, 0, 50, 20)),
            root.repaints,
            "only the area of the child whose place among its siblings changed should be repainted",
        )
    }

    @Test
    fun onEndChanges_leavesAnInsertedChildToTheRelayoutThatSizesIt() = runSwingTest {
        val root = CountingPanel()
        val applier = applierFor(root)
        seedRow(root, applier)
        val relayoutsBefore = root.revalidateCount

        // A new child has no bounds yet, so it covers no area: the relayout that gives it some repaints it,
        // and a paint asked for here would only be made again by that relayout.
        applier.onBeginChanges()
        applier.onContainer(applier.root) { insertChild(3, SwingNodeHolder(namedButton("d"))) }
        applier.onEndChanges()

        assertEquals(listOf("a", "b", "c", "d"), childNames(root), "the child should be inserted")
        assertEquals(
            relayoutsBefore + 1,
            root.revalidateCount,
            "the insert should ask for the relayout that sizes the child",
        )
        assertEquals(emptyList(), root.repaints, "and for no paint of its own, which that relayout makes")
    }

    @Test
    fun onEndChanges_repaintsTheAreaOfAnInsertedChildThatAlreadyHasBounds() = runSwingTest {
        val root = CountingPanel()
        val applier = applierFor(root)
        seedRow(root, applier)

        // Bounds written before the child has a parent, as a geometry modifier writes them, repaint nothing,
        // and a host without a layout manager gives the child no bounds to repaint it through.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertChild(3, SwingNodeHolder(namedButton("d").apply { setBounds(150, 0, 50, 20) }))
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "b", "c", "d"), childNames(root), "the child should be inserted")
        assertEquals(
            listOf(Rectangle(150, 0, 50, 20)),
            root.repaints,
            "only the area the inserted child covers should be repainted",
        )
    }

    @Test
    fun mutatingNestedContainerAppliesChildAndRevalidatesThatContainerNotRoot() = runSwingTest {
        val root = CountingPanel()
        val applier = applierFor(root)
        // The nested container counts its own revalidate() calls so we can prove the applier
        // targeted and revalidated the inner panel, not the root.
        val childPanel = CountingPanel().apply { name = "child" }

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertChild(0, SwingNodeHolder(childPanel))
        }
        applier.onEndChanges()

        val rootRevalidatesAfterFirstPass = root.revalidateCount
        val childRevalidatesAfterFirstPass = childPanel.revalidateCount

        // Second pass: descend into the child container and add a leaf there.
        applier.onBeginChanges()
        applier.onContainer(SwingNodeHolder(childPanel)) {
            insertChild(0, SwingNodeHolder(JLabel("inner")))
        }
        applier.onEndChanges()

        assertEquals(1, childPanel.componentCount, "the leaf should land in the child container")
        assertEquals(1, root.componentCount, "the root should still hold only the child panel")
        assertSame(childPanel, root.getComponent(0), "the child panel should remain the root's only child")
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
}
