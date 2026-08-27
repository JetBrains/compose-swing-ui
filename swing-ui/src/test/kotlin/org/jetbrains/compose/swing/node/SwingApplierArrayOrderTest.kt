package org.jetbrains.compose.swing.node

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The AWT component-array order [SwingApplier] produces always matches the composition insertion
 * index, for BOTH constrained and unconstrained children. Everything else the applier does addresses
 * that array by index - [SwingApplier.remove] and [SwingApplier.move] included - so a constrained add
 * that appended instead of inserting at its index would leave every later index pointing at the wrong
 * component.
 */
class SwingApplierArrayOrderTest {
    private fun SwingApplier.onContainer(
        holder: SwingNodeHolder<*>,
        block: SwingApplier.() -> Unit,
    ) {
        down(holder)
        block()
        up()
    }

    private fun namedButton(name: String): JButton = JButton(name).apply { this.name = name }

    private fun childNames(container: Container): List<String> = container.components.map { it.name }

    private fun constrainedHolder(
        component: Component,
        constraint: Any,
    ): SwingNodeHolder<*> = SwingNodeHolder(component).also { it.applyConstraint(constraint) }

    private fun holder(component: Component): SwingNodeHolder<*> = SwingNodeHolder(component)

    private val owners = mutableListOf<TestCompositionOwner>()

    /**
     * Builds a [SwingApplier] over [root]. Production disposes the composition owner when the
     * composition unmounts; without a composition here, this test disposes it itself in [disposeOwners].
     */
    private fun applierFor(root: Container): SwingApplier {
        val owner = TestCompositionOwner.observing()
        owners += owner
        return SwingApplier(SwingNodeHolder(root).attachedTo(owner))
    }

    @AfterTest
    fun disposeOwners() {
        owners.forEach { it.dispose() }
        owners.clear()
    }

    /**
     * A BorderLayout container receives a mix of constrained and unconstrained children at ascending
     * composition indices. The component-array order must equal the insertion order, proving the
     * constrained adds inserted at their index rather than appending.
     */
    @Test
    fun componentArrayOrderMatchesInsertionIndexForMixedAdds() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(namedButton("north"), BorderLayout.NORTH))
            insertBottomUp(1, constrainedHolder(namedButton("center"), BorderLayout.CENTER))
            insertBottomUp(2, constrainedHolder(namedButton("south"), BorderLayout.SOUTH))
        }
        applier.onEndChanges()

        // Array order equals composition order, not BorderLayout's own region order.
        assertEquals(listOf("north", "center", "south"), childNames(root), "array order should match composition order")
        // Index and constraint come from one Container.add(component, constraint, index) call, so an
        // ordering test asserts both: a two-argument indexed add still passes the order check above,
        // and a three-argument append still passes SwingApplierConstraintTest.
        val layout = root.layout as BorderLayout
        assertEquals(
            BorderLayout.NORTH,
            layout.getConstraints(root.getComponent(0)),
            "child 0 should carry the NORTH constraint",
        )
        assertEquals(
            BorderLayout.CENTER,
            layout.getConstraints(root.getComponent(1)),
            "child 1 should carry the CENTER constraint",
        )
        assertEquals(
            BorderLayout.SOUTH,
            layout.getConstraints(root.getComponent(2)),
            "child 2 should carry the SOUTH constraint",
        )
    }

    /**
     * A BorderLayout container that starts WITHOUT a north child, then gets one inserted at
     * composition index 0. The new child must land at array index 0 rather than at the end, so every
     * subsequent index-based operation still addresses the child its composition index names.
     */
    @Test
    fun constrainedInsertAtIndexZeroLandsFirstInArrayNotAppended() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)

        // Start with [center, south] (north slot absent), matching the BorderPanel slot order when
        // the conditional north is off - north occupies no composition index.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(namedButton("center"), BorderLayout.CENTER))
            insertBottomUp(1, constrainedHolder(namedButton("south"), BorderLayout.SOUTH))
        }
        applier.onEndChanges()
        assertEquals(listOf("center", "south"), childNames(root), "the container should start without a north child")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(namedButton("north"), BorderLayout.NORTH))
        }
        applier.onEndChanges()

        assertEquals(
            listOf("north", "center", "south"),
            childNames(root),
            "the index-0 insert should land first, not append",
        )
    }

    /**
     * `remove(0, 1)` drops the composition-index-0 child even in a BorderLayout container, whose own
     * region order differs from the array order.
     */
    @Test
    fun removeIndexZeroRemovesCompositionIndexZeroChildInBorderLayout() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)
        val north = namedButton("north")
        val center = namedButton("center")
        val south = namedButton("south")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(north, BorderLayout.NORTH))
            insertBottomUp(1, constrainedHolder(center, BorderLayout.CENTER))
            insertBottomUp(2, constrainedHolder(south, BorderLayout.SOUTH))
        }
        applier.onEndChanges()
        assertEquals(
            listOf("north", "center", "south"),
            childNames(root),
            "all three children should be present before removal",
        )

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            remove(0, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("center", "south"), childNames(root), "remove(0) should drop the NORTH child")
        val layout = root.layout as BorderLayout
        assertSame(center, root.getComponent(0), "the CENTER instance should remain at array index 0")
        assertSame(south, root.getComponent(1), "the SOUTH instance should remain at array index 1")
        assertEquals(BorderLayout.CENTER, layout.getConstraints(center), "the CENTER child should keep its constraint")
        assertEquals(BorderLayout.SOUTH, layout.getConstraints(south), "the SOUTH child should keep its constraint")
        assertNull(layout.getConstraints(north), "the removed NORTH child should have no constraint association")
    }

    /**
     * Mixed constrained + unconstrained adds interleaved by index must still produce array order ==
     * composition order (the unconstrained ones also insert at their index, not append).
     */
    @Test
    fun mixedConstrainedAndUnconstrainedKeepArrayOrder() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(namedButton("north"), BorderLayout.NORTH))
            insertBottomUp(1, holder(namedButton("plain")))
            insertBottomUp(2, constrainedHolder(namedButton("south"), BorderLayout.SOUTH))
        }
        applier.onEndChanges()

        assertEquals(listOf("north", "plain", "south"), childNames(root))
    }
}
