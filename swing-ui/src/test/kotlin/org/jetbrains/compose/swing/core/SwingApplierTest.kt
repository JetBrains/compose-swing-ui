package org.jetbrains.compose.swing.core

import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.ChildPlacement
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.Toolkit
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
     * Lets a check the applier deferred to a later turn of the event queue - see
     * [SwingApplier][org.jetbrains.compose.swing.node.SwingApplier]'s hold-to-regions pass - run before
     * this returns, and rethrows here whatever it raises rather than leaving it to the event dispatch
     * thread's own uncaught-exception handling, which would otherwise only print it.
     *
     * Must be called on the EDT, which is where every mutation in this file already runs; entering a
     * [java.awt.SecondaryLoop] pumps the queue for one further turn without leaving the thread.
     */
    private fun pumpScheduledWork() {
        val thread = Thread.currentThread()
        val enclosingHandler = thread.uncaughtExceptionHandler
        var failure: Throwable? = null
        thread.setUncaughtExceptionHandler { _, raised -> failure = raised }
        try {
            val loop = Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()
            SwingUtilities.invokeLater { loop.exit() }
            loop.enter()
        } finally {
            thread.setUncaughtExceptionHandler(enclosingHandler)
        }
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

    /**
     * Runs the update changes a recomposition applies to [node]. The applier is positioned at a node
     * while that node's own update runs and returns to its host as the node's group ends, so this is the
     * shape in which a change to what a node declares reaches the applier at all.
     */
    private fun SwingApplier.onNode(
        node: SwingNodeHolder<*>,
        update: () -> Unit,
    ): Unit = onContainer(node) { update() }

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

    /**
     * Hands [instance] to the applier the way the runtime hands over a node it relocates - a
     * `movableContent` invoked under another parent: bottom-up first and top-down after, back to back, with
     * nothing of the node's own in between. What the node carries as it arrives is therefore the placement
     * it named at the host it is leaving; the chain that names its placement here runs later in the pass.
     */
    private fun SwingApplier.relocateChild(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        insertBottomUp(index, instance)
        insertTopDown(index, instance)
    }

    private fun holder(component: Component): SwingNodeHolder<Component> = SwingNodeHolder(component)

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
    fun insert_addsChildToCurrentContainer() {
        val root = JPanel()
        val applier = applierFor(root)
        val child = namedButton("a")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertChild(0, holder(child))
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
            insertChild(0, holder(namedButton("a")))
            insertChild(1, holder(namedButton("b")))
            // Insert "c" between a and b.
            insertChild(1, holder(namedButton("c")))
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
                insertChild(i, holder(namedButton(n)))
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
                insertChild(i, holder(namedButton(n)))
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
                insertChild(i, holder(namedButton(n)))
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
                insertChild(i, holder(namedButton(n)))
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
                insertChild(i, holder(namedButton(n)))
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
                insertChild(i, holder(namedButton(n)))
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
                insertChild(i, holder(namedButton(n)))
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
            insertChild(0, holder(namedButton("a")))
            insertChild(1, holder(namedButton("b")))
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
            insertChild(0, holder(namedButton("a")))
            insertChild(1, holder(namedButton("b")))
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
            insertChild(0, holder(childPanel))
        }
        applier.onEndChanges()

        val rootRevalidatesAfterFirstPass = root.revalidateCount
        val childRevalidatesAfterFirstPass = childPanel.revalidateCount

        // Second pass: descend into the child container and add a leaf there.
        applier.onBeginChanges()
        applier.onContainer(holder(childPanel)) {
            insertChild(0, holder(JLabel("inner")))
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

    /**
     * A holder whose node declares that it fills the host region [name], through [attachment] - the same
     * channel a component's own `update` declares it through, so the node records the region exactly as a
     * composed one does.
     */
    private fun slotHolder(
        component: Component,
        name: String = VIEWPORT_CALL,
        attachment: SlotAttachment = HoldsNothing,
    ): SwingNodeHolder<Component> = holder(component).apply { applyModifierDiff(SwingModifier.slot(name, attachment)) }

    /** A `JSplitPane` holding its children on the two sides it offers, with neither side taken. */
    private fun splitHost(pane: JSplitPane): SwingNodeHolder<Component> =
        holder(pane).apply { childPlacement = SplitSides }

    @Test
    fun aChildFillingARegionIsRefusedByAHostThatAddsItsChildrenByIndex() = onEdt {
        // The host states no placement, so it holds every child by index and offers no region at all:
        // the container offering the one this child names is somewhere else in the composition.
        val applier = applierFor(JPanel())

        val failure =
            assertFailsWith<IllegalStateException> { applier.insertChild(0, slotHolder(JLabel("in a region"))) }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("offers no regions of its own"), "the failure should say why: $message")
        assertTrue(message.contains("JPanel"), "the failure should name the host: $message")
        assertTrue(
            message.contains("Declare the child without $VIEWPORT_CALL"),
            "the failure should name the edit that places the child: $message",
        )
    }

    @Test
    fun aChildAddedByIndexIsRefusedByAHostThatHoldsItsChildrenInRegions() = onEdt {
        // The host states a region-holding placement, so it reaches every child through a setter of its
        // own and a child merely added to it would be held by the host and laid out by nobody.
        val applier = applierFor(JPanel())
        applier.root.childPlacement = ChildPlacement.Slots(VIEWPORT_CALL)

        val failure = assertFailsWith<IllegalStateException> { applier.insertChild(0, holder(JLabel("by index"))) }

        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("holds each child in one of its own regions"),
            "the failure should say why: $message",
        )
        assertTrue(
            message.contains("Add $VIEWPORT_CALL."),
            "the failure should name the call that would place the child: $message",
        )
    }

    @Test
    fun aHostStatingAnotherPlacementOverTheChildrenItHoldsIsRefused() = onEdt {
        // A node's children are one index space, and the two kinds are reached through different Swing
        // calls, so the placement a host states holds for as long as that host holds children.
        val applier = applierFor(JPanel())
        applier.insertChild(0, holder(JLabel("by index")))

        applier.root.childPlacement = ChildPlacement.Slots(VIEWPORT_CALL)
        val failure =
            assertFailsWith<IllegalStateException> { applier.insertChild(1, slotHolder(JLabel("in a region"))) }

        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("already holds children added by index"),
            "the failure should say what the host already holds: $message",
        )
        assertTrue(
            message.contains("key(childPlacement)"),
            "the failure should name what to write instead: $message",
        )
    }

    @Test
    fun aChildNamingAnotherRegionIsMovedThereAndLeavesTheSiblingTheRegionItTakes() = onEdt {
        val applier = applierFor(JPanel())
        val pane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, false, null, null)
        val host = splitHost(pane)
        val moved = namedButton("moved")
        val arriving = namedButton("arriving")
        val movedChild = slotHolder(moved, FIRST_SIDE_CALL, FirstSideAttachment)

        applier.onBeginChanges()
        applier.onContainer(applier.root) { insertChild(0, host) }
        applier.onContainer(host) { insertChild(0, movedChild) }
        applier.onEndChanges()
        assertSame(moved, pane.leftComponent, "the child should start on the side its chain named")

        // The child names the trailing side while a sibling arrives on the leading one. A JSplitPane
        // gives a side away by taking out whatever it holds there, so the arriving sibling takes the
        // leading side off the child that is still physically on it.
        applier.onBeginChanges()
        applier.onContainer(host) {
            onNode(movedChild) {
                movedChild.applyModifierDiff(SwingModifier.slot(SECOND_SIDE_CALL, SecondSideAttachment))
            }
            insertChild(1, slotHolder(arriving, FIRST_SIDE_CALL, FirstSideAttachment))
        }
        applier.onEndChanges()

        assertSame(arriving, pane.leftComponent, "the arriving sibling should hold the side it named")
        assertSame(moved, pane.rightComponent, "the child naming another side should be moved onto it")
    }

    @Test
    fun aChildThatStopsNamingARegionReleasesItAndIsRefused() = onEdt {
        val applier = applierFor(JPanel())
        val pane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, false, null, null)
        val host = splitHost(pane)
        val child = slotHolder(namedButton("leading"), FIRST_SIDE_CALL, FirstSideAttachment)

        applier.onBeginChanges()
        applier.onContainer(applier.root) { insertChild(0, host) }
        applier.onContainer(host) { insertChild(0, child) }
        applier.onEndChanges()

        // The chain names no region at all any more. A JSplitPane holds every child on a side of its
        // own, so the side is released and the child, which the pane would hold and nobody would lay
        // out, is refused the way one arriving without a side is.
        applier.onBeginChanges()
        applier.onContainer(host) { onNode(child) { child.applyModifierDiff(SwingModifier) } }
        val failure = assertFailsWith<IllegalStateException> { applier.onEndChanges() }

        assertNull(pane.leftComponent, "the side the child gave up should be released")
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("holds each child in one of its own regions"),
            "the failure should say why: $message",
        )
        assertTrue(
            message.contains("Add one of: $FIRST_SIDE_CALL"),
            "the failure should name the calls that would place the child: $message",
        )
    }

    @Test
    fun twoChildrenLeftInOneRegionByAMoveAreReported() = onEdt {
        val applier = applierFor(JPanel())
        val pane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, false, null, null)
        val host = splitHost(pane)
        val leading = slotHolder(namedButton("leading"), FIRST_SIDE_CALL, FirstSideAttachment)
        val trailing = slotHolder(JLabel("trailing"), SECOND_SIDE_CALL, SecondSideAttachment)

        applier.onBeginChanges()
        applier.onContainer(applier.root) { insertChild(0, host) }
        applier.onContainer(host) {
            insertChild(0, leading)
            insertChild(1, trailing)
        }
        applier.onEndChanges()

        // Both children end the pass naming the trailing side, which shows one component: the move is
        // what puts them there, so it is the moved child's new side the pane is held to.
        applier.onBeginChanges()
        applier.onContainer(host) {
            onNode(leading) { leading.applyModifierDiff(SwingModifier.slot(SECOND_SIDE_CALL, SecondSideAttachment)) }
        }
        val failure =
            assertFailsWith<IllegalStateException> {
                applier.onEndChanges()
                pumpScheduledWork()
            }

        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("holds one component per region"),
            "the failure should say why: $message",
        )
        assertTrue(
            message.contains(SECOND_SIDE_CALL),
            "the failure should name the region two children declare: $message",
        )
    }

    @Test
    fun aRegionWhoseOccupantOnePassReplacesIsNotReported() = onEdt {
        val applier = applierFor(JPanel())
        val pane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, false, null, null)
        val host = splitHost(pane)
        val incoming = namedButton("incoming")

        applier.onBeginChanges()
        applier.onContainer(applier.root) { insertChild(0, host) }
        applier.onContainer(host) {
            insertChild(0, slotHolder(namedButton("outgoing"), FIRST_SIDE_CALL, FirstSideAttachment))
        }
        applier.onEndChanges()

        // A keyed swap of what fills a side inserts the replacement before dropping the child it
        // replaces, so the pane holds two children on one side while the pass runs. Only what remains
        // once the pass has settled is what the composition declares, and that is a single occupant.
        applier.onBeginChanges()
        applier.onContainer(host) {
            insertChild(0, slotHolder(incoming, FIRST_SIDE_CALL, FirstSideAttachment))
            remove(1, 1)
        }
        applier.onEndChanges()

        assertSame(incoming, pane.leftComponent, "the side should be held by the child that replaced its occupant")
    }

    @Test
    fun aRelocatedChildIsAttachedOnceTheChangePassHasSettled() = onEdt {
        val root = JPanel()
        val applier = applierFor(root)
        val moved = holder(namedButton("moved"))

        // The relocated child stands between two freshly composed siblings, so the place it takes is one
        // the pass has to count rather than compose: while it waits, the sibling after it is attached at
        // the position the children already attached give it.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertChild(0, holder(namedButton("a")))
            relocateChild(1, moved)
            insertChild(2, holder(namedButton("b")))
        }

        assertEquals(listOf("a", "b"), childNames(root), "a relocated child is not attached as it arrives")

        applier.onEndChanges()

        assertEquals(listOf("a", "moved", "b"), childNames(root), "it is attached where it is composed")
    }

    @Test
    fun aRelocatedChildFillsTheRegionItNamesAtTheHostItArrivesAt() = onEdt {
        val applier = applierFor(JPanel())
        val leftPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, false, null, null)
        val rightPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, false, null, null)
        val leaving = splitHost(leftPane)
        val arrivedAt = splitHost(rightPane)
        val moved = namedButton("moved")
        val child = slotHolder(moved, FIRST_SIDE_CALL, FirstSideAttachment)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertChild(0, leaving)
            insertChild(1, arrivedAt)
        }
        applier.onContainer(leaving) { insertChild(0, child) }
        applier.onEndChanges()
        assertSame(moved, leftPane.leftComponent, "the child should start on the side its chain named")

        // The composition moves the child to the other pane. It arrives still carrying the side it named
        // at the pane it is leaving, and only afterwards does its chain name the side it fills here.
        applier.onBeginChanges()
        applier.onContainer(leaving) { remove(0, 1) }
        applier.onContainer(arrivedAt) {
            relocateChild(0, child)
            onNode(child) { child.applyModifierDiff(SwingModifier.slot(SECOND_SIDE_CALL, SecondSideAttachment)) }
        }
        applier.onEndChanges()

        assertNull(leftPane.leftComponent, "the side it left should hold nothing")
        assertSame(moved, rightPane.rightComponent, "the side it names at the pane it moved to should hold it")
        assertNull(rightPane.leftComponent, "and no other side of that pane should hold it")
    }

    @Test
    fun aRelocatedChildFillingARegionIsRefusedByAHostThatAddsItsChildrenByIndex() = onEdt {
        // The host states no placement, so it holds every child by index and offers no region at all. The
        // child is held to that once the pass has settled, which is when the region it names here is known.
        val applier = applierFor(JPanel())

        applier.onBeginChanges()
        applier.onContainer(applier.root) { relocateChild(0, slotHolder(JLabel("in a region"))) }
        val failure = assertFailsWith<IllegalStateException> { applier.onEndChanges() }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("offers no regions of its own"), "the failure should say why: $message")
        assertTrue(message.contains("JPanel"), "the failure should name the host: $message")
        assertTrue(
            message.contains("Declare the child without $VIEWPORT_CALL"),
            "the failure should name the edit that places the child: $message",
        )
    }

    @Test
    fun aRelocatedChildAddedByIndexIsRefusedByAHostThatHoldsItsChildrenInRegions() = onEdt {
        // The host reaches every child through a setter of its own, so one that ends the pass naming no
        // region would be held by the host and laid out by nobody, whichever way it arrived.
        val applier = applierFor(JPanel())
        applier.root.childPlacement = ChildPlacement.Slots(VIEWPORT_CALL)

        applier.onBeginChanges()
        applier.onContainer(applier.root) { relocateChild(0, holder(JLabel("by index"))) }
        val failure = assertFailsWith<IllegalStateException> { applier.onEndChanges() }

        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("holds each child in one of its own regions"),
            "the failure should say why: $message",
        )
        assertTrue(
            message.contains("Add $VIEWPORT_CALL."),
            "the failure should name the call that would place the child: $message",
        )
    }

    @Test
    fun aChainNamingBothARegionAndALayoutConstraintIsRefused() {
        // A parent holds a child by one of the two, so a chain declaring both says something no parent
        // can carry out, and neither placement is recorded.
        val child = holder(JLabel("placed twice"))

        val failure =
            assertFailsWith<IllegalStateException> {
                child.applyModifierDiff(
                    SwingModifier.layoutConstraint(BorderLayout.CENTER).slot(VIEWPORT_CALL, HoldsNothing),
                )
            }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("this chain declares both"), "the failure should say why: $message")
        assertTrue(
            message.contains("layoutConstraint(${BorderLayout.CENTER}) and $VIEWPORT_CALL"),
            "the failure should name the two placements the chain declares: $message",
        )
        assertNull(child.declaredSlotName, "a refused chain should leave no region recorded on the node")
        assertNull(child.constraint, "a refused chain should leave no layout constraint recorded on the node")
    }
}

/** A region call standing in for the ones a real container's scope offers. */
private const val VIEWPORT_CALL: String = "SwingModifier.viewport()"

/** The call filling the leading side of a `JSplitPane`, as a child of one writes it. */
private const val FIRST_SIDE_CALL: String = "SwingModifier.first()"

/** The call filling the trailing side of a `JSplitPane`, as a child of one writes it. */
private const val SECOND_SIDE_CALL: String = "SwingModifier.second()"

/** The two sides a `JSplitPane` holds its children on, each showing a single component. */
private val SplitSides: ChildPlacement = ChildPlacement.Slots(FIRST_SIDE_CALL, SECOND_SIDE_CALL)

/**
 * Installs a child on the leading side of the host `JSplitPane`, and releases that side for the child
 * that installed it - the shape every attachment a container's scope hands out has, so a side a sibling
 * has already taken over is left to that sibling.
 */
private val FirstSideAttachment =
    SlotAttachment { host, component, _ ->
        val pane = host as JSplitPane
        pane.leftComponent = component
        return@SlotAttachment { if (pane.leftComponent === component) pane.leftComponent = null }
    }

/** Installs a child on the trailing side of the host `JSplitPane`, releasing it as [FirstSideAttachment] does. */
private val SecondSideAttachment =
    SlotAttachment { host, component, _ ->
        val pane = host as JSplitPane
        pane.rightComponent = component
        return@SlotAttachment { if (pane.rightComponent === component) pane.rightComponent = null }
    }

/** An attachment for a host of no consequence to a test: it installs nothing and releases nothing. */
private val HoldsNothing = SlotAttachment { _, _, _ -> {} }
