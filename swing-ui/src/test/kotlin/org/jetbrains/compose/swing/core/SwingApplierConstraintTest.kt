package org.jetbrains.compose.swing.core

import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SwingApplier] honours [SwingNodeHolder.constraint] when adding components to a constrained layout
 * (here [BorderLayout]), and preserves each component's constraint across the remove/re-add
 * [SwingApplier.move] performs internally - Swing itself drops a child's constraint on `remove`, so
 * the applier has to carry it across.
 */
class SwingApplierConstraintTest {
    private fun SwingApplier.onContainer(
        holder: SwingNodeHolder<*>,
        block: SwingApplier.() -> Unit,
    ) {
        down(holder)
        block()
        up()
    }

    private fun constraintOf(
        parent: JPanel,
        child: Component,
    ): Any? = (parent.layout as BorderLayout).getConstraints(child)

    private fun constrainedHolder(
        component: Component,
        constraint: Any,
    ): SwingNodeHolder<*> = SwingNodeHolder(component).also { it.applyConstraint(constraint) }

    private val observers = mutableListOf<SnapshotStateObserver>()

    /**
     * Builds a [SwingApplier] over [root]. Production disposes its snapshot observer when the
     * composition unmounts; without a composition here, this test disposes it itself in [disposeObservers].
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

    @Test
    fun constrainedChildIsRetrievableViaBorderLayoutGetConstraints() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)
        val south = JButton("south")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(south, BorderLayout.SOUTH))
        }
        applier.onEndChanges()

        assertEquals(BorderLayout.SOUTH, constraintOf(root, south), "the inserted child kept its SOUTH constraint")
    }

    @Test
    fun movePreservesEachConstraintAfterReorder() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)
        val north = JButton("north")
        val center = JButton("center")
        val south = JButton("south")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(north, BorderLayout.NORTH))
            insertBottomUp(1, constrainedHolder(center, BorderLayout.CENTER))
            insertBottomUp(2, constrainedHolder(south, BorderLayout.SOUTH))
        }
        applier.onEndChanges()

        // Move the first child to the end; BorderLayout ignores index, so the only observable
        // effect must be that constraints survive the internal remove/re-add.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            move(0, 3, 1)
        }
        applier.onEndChanges()

        assertEquals(BorderLayout.NORTH, constraintOf(root, north), "north lost its constraint across the move")
        assertEquals(BorderLayout.CENTER, constraintOf(root, center), "center lost its constraint across the move")
        assertEquals(BorderLayout.SOUTH, constraintOf(root, south), "south lost its constraint across the move")
        assertEquals(3, root.componentCount, "the reorder must not drop or duplicate any child")
    }

    @Test
    fun moveMultiCountPreservesConstraintsForWholeRun() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)
        val north = JButton("north")
        val center = JButton("center")
        val south = JButton("south")
        val east = JButton("east")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(north, BorderLayout.NORTH))
            insertBottomUp(1, constrainedHolder(center, BorderLayout.CENTER))
            insertBottomUp(2, constrainedHolder(south, BorderLayout.SOUTH))
            insertBottomUp(3, constrainedHolder(east, BorderLayout.EAST))
        }
        applier.onEndChanges()

        // Move a 2-run [north, center] to the end.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            move(0, 4, 2)
        }
        applier.onEndChanges()

        assertEquals(BorderLayout.NORTH, constraintOf(root, north), "north lost its constraint across the multi-move")
        assertEquals(
            BorderLayout.CENTER,
            constraintOf(root, center),
            "center lost its constraint across the multi-move",
        )
        assertEquals(BorderLayout.SOUTH, constraintOf(root, south), "south lost its constraint across the multi-move")
        assertEquals(BorderLayout.EAST, constraintOf(root, east), "east lost its constraint across the multi-move")
    }

    @Test
    fun removeForgetsConstraintSoStaleEntryDoesNotLeak() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)
        val south = JButton("south")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(south, BorderLayout.SOUTH))
        }
        applier.onEndChanges()
        assertEquals(BorderLayout.SOUTH, constraintOf(root, south), "the inserted child kept its SOUTH constraint")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            remove(0, 1)
        }
        applier.onEndChanges()

        assertEquals(0, root.componentCount, "remove must detach the child from the parent")
        assertEquals(null, constraintOf(root, south), "a removed child must leave no stale constraint entry")
    }
}
