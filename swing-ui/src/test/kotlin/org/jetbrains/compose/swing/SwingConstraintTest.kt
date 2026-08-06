package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingConstraint
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.LayoutManager
import java.awt.LayoutManager2
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [SwingConstraint], the placement a container declares for the children it
 * composes.
 *
 * Every container here is built the way an application would build one - its own panel, its own
 * layout manager, nothing but the published API - and every assertion reads the live AWT tree: the
 * parent's layout manager and its component array, exactly what such an application sees.
 */
class SwingConstraintTest {
    /** A container over a layout manager the library wraps, hosting free-form children. */
    @Composable
    @SwingComposable
    private fun BorderContainer(
        modifier: SwingModifier = SwingModifier,
        content:
            @Composable @SwingComposable
            () -> Unit,
    ) {
        SwingNode(
            factory = { JPanel(BorderLayout()) },
            update = { applyModifier(modifier) },
            content = content,
        )
    }

    /** A container whose manager takes no constraints at all. */
    @Composable
    @SwingComposable
    private fun FlowContainer(
        modifier: SwingModifier = SwingModifier,
        content:
            @Composable @SwingComposable
            () -> Unit,
    ) {
        SwingNode(
            factory = { JPanel(FlowLayout()) },
            update = { applyModifier(modifier) },
            content = content,
        )
    }

    /** A container whose manager accepts only its own constraint type. */
    @Composable
    @SwingComposable
    private fun GridBagContainer(
        modifier: SwingModifier = SwingModifier,
        content:
            @Composable @SwingComposable
            () -> Unit,
    ) {
        SwingNode(
            factory = { JPanel(GridBagLayout()) },
            update = { applyModifier(modifier) },
            content = content,
        )
    }

    /** A container whose manager reports every placement it is handed; see [RecordingLayout]. */
    @Composable
    @SwingComposable
    private fun RecordingContainer(
        layout: RecordingLayout,
        content:
            @Composable @SwingComposable
            () -> Unit,
    ) {
        SwingNode(factory = { JPanel(layout) }, content = content)
    }

    /**
     * A container over a constraint type and a layout manager of the caller's own, offering its
     * placements as a receiver DSL. [MosaicScope.cell] is the whole of the placement API its callers
     * see: the panel itself places every child it composes.
     */
    @Composable
    @SwingComposable
    private fun MosaicPanel(
        modifier: SwingModifier = SwingModifier,
        block: MosaicScope.() -> Unit,
    ) {
        val scope = MosaicScopeImpl().apply(block)
        SwingNode(
            factory = { JPanel(MosaicLayout()) },
            update = { applyModifier(modifier) },
            content = {
                scope.cells.forEach { (cell, content) ->
                    key(cell) {
                        SwingConstraint(cell) { content() }
                    }
                }
            },
        )
    }

    @Test
    fun aContainerPlacesTheChildrenItComposesThroughItsOwnScope() = runComposeSwingTest {
        setContent {
            MosaicPanel {
                cell(row = 0, column = 0) { Label("title") }
                cell(row = 1, column = 0) { Label("body") }
            }
        }

        val title = onNodeWithText("title")
        val mosaic = managerPlacing<MosaicLayout>("title")
        assertEquals(MosaicCell(row = 0, column = 0), mosaic.cellOf(title.fetch()), "title")
        assertEquals(MosaicCell(row = 1, column = 0), mosaic.cellOf(onNodeWithText("body").fetch()), "body")
        title.onParent().onChildren().assertCountEquals(2)
    }

    @Test
    fun aChangedConstraintMovesTheChildWithinItsScope() = runComposeSwingTest {
        var row by mutableStateOf(0)
        setContent {
            MosaicPanel {
                cell(row = row, column = 2) { Label("mover") }
            }
        }
        val mover = onNodeWithText("mover")
        assertEquals(
            MosaicCell(row = 0, column = 2),
            managerPlacing<MosaicLayout>("mover").cellOf(mover.fetch()),
            "first cell",
        )

        row = 3
        awaitIdle()

        assertEquals(
            MosaicCell(row = 3, column = 2),
            managerPlacing<MosaicLayout>("mover").cellOf(mover.fetch()),
            "the child moved to its new cell",
        )
        // The child moved cell without moving in the component array.
        val panel = mover.onParent()
        panel.onChildren().assertCountEquals(1)
        panel.onChildAt(0).assertTextEquals("mover")
    }

    @Test
    fun placedChildrenSitInTheComponentArrayInDeclarationOrder() = runComposeSwingTest {
        var withSubtitle by mutableStateOf(false)
        setContent {
            MosaicPanel {
                cell(row = 0, column = 0) { Label("title") }
                if (withSubtitle) cell(row = 1, column = 0) { Label("subtitle") }
                cell(row = 2, column = 0) { Label("body") }
            }
        }
        val panel = onNodeWithText("title").onParent()
        panel.onChildAt(0).assertTextEquals("title")
        panel.onChildAt(1).assertTextEquals("body")

        withSubtitle = true
        awaitIdle()

        // A declaration that appears between two others takes its own place in the component array,
        // constraint and all, so a layout manager reading that array reads the structure the caller
        // declared, in the order they declared it.
        panel.onChildren().assertCountEquals(3)
        panel.onChildAt(0).assertTextEquals("title")
        panel.onChildAt(1).assertTextEquals("subtitle")
        panel.onChildAt(2).assertTextEquals("body")
    }

    @Test
    fun onePlacementCoversEveryChildDeclaredUnderIt() = runComposeSwingTest {
        setContent {
            MosaicPanel {
                cell(row = 0, column = 0) {
                    Label("name")
                    Label("value")
                }
            }
        }

        // A placement reaches every component its content emits, not just a first one, so a container
        // is free to offer a declaration that covers a whole run of children.
        val cell = MosaicCell(row = 0, column = 0)
        val mosaic = managerPlacing<MosaicLayout>("name")
        assertEquals(cell, mosaic.cellOf(onNodeWithText("name").fetch()), "name")
        assertEquals(cell, mosaic.cellOf(onNodeWithText("value").fetch()), "value")
    }

    @Test
    fun aConstraintPlacesTheChildInItsRegion() = runComposeSwingTest {
        setContent {
            BorderContainer {
                SwingConstraint(BorderLayout.NORTH) { Label("header") }
                SwingConstraint(BorderLayout.CENTER) { Label("body") }
            }
        }

        onNodeWithText("header").assertLayoutConstraint(BorderLayout.NORTH)
        onNodeWithText("body").assertLayoutConstraint(BorderLayout.CENTER)
    }

    @Test
    fun aChangedConstraintMovesTheChildToItsNewRegion() = runComposeSwingTest {
        var region by mutableStateOf(BorderLayout.NORTH)
        setContent {
            BorderContainer {
                SwingConstraint(region) { Label("mover") }
            }
        }
        val mover = onNodeWithText("mover")
        mover.assertLayoutConstraint(BorderLayout.NORTH)

        region = BorderLayout.SOUTH
        awaitIdle()

        mover.assertLayoutConstraint(BorderLayout.SOUTH)
        // The old region is vacated: a manager that keys children by region would otherwise report the
        // same component in both places.
        assertNull(
            managerPlacing<BorderLayout>("mover").getLayoutComponent(BorderLayout.NORTH),
            "the north region is vacated",
        )
        mover.onParent().onChildAt(0).assertTextEquals("mover")
    }

    @Test
    fun aChangedConstraintReachesTheLayoutManagerExactlyOnce() = runComposeSwingTest {
        val layout = RecordingLayout()
        var region by mutableStateOf(BorderLayout.NORTH)
        var text by mutableStateOf("one")
        setContent {
            RecordingContainer(layout) {
                SwingConstraint(region) { Label(text) }
            }
        }
        assertEquals(listOf<Any?>(BorderLayout.NORTH), layout.registered, "attaching registers the child once")

        // A composition that leaves the placement alone must not touch the layout manager at all: the
        // child keeps the registration it already has.
        text = "two"
        awaitIdle()
        assertEquals(listOf<Any?>(BorderLayout.NORTH), layout.registered, "an unchanged placement is not re-registered")
        assertEquals(0, layout.unregistered, "an unchanged placement is not cleared")

        region = BorderLayout.SOUTH
        awaitIdle()
        assertEquals(
            listOf<Any?>(BorderLayout.NORTH, BorderLayout.SOUTH),
            layout.registered,
            "the new placement is registered once",
        )
        assertEquals(1, layout.unregistered, "the old placement is cleared once")
    }

    @Test
    fun aChildMovingBetweenContainersIsPlacedByItsNewOne() = runComposeSwingTest {
        var inRegion by mutableStateOf(false)
        setContent {
            // One child, composed either under a region of the enclosing BorderPanel or under a
            // constraint an inner container declares. Movable content carries the same node between
            // the two, so the child changes which container places it without being recreated.
            val child = remember { movableContentOf { Label("mover") } }
            BorderPanel {
                if (inRegion) {
                    north { child() }
                } else {
                    center { BorderContainer { SwingConstraint(BorderLayout.EAST) { child() } } }
                }
            }
        }
        val mover = onNodeOfType<JLabel>()
        mover.assertLayoutConstraint(BorderLayout.EAST)

        inRegion = true
        awaitIdle()

        mover.assertLayoutConstraint(BorderLayout.NORTH)
    }

    @Test
    fun aChildOutsideAnyConstraintKeepsTheOneItsParentSlotProvides() = runComposeSwingTest {
        setContent {
            BorderPanel {
                north { Label("top") }
                center { Label("middle") }
            }
        }

        onNodeWithText("top").assertLayoutConstraint(BorderLayout.NORTH)
        onNodeWithText("middle").assertLayoutConstraint(BorderLayout.CENTER)
    }

    @Test
    fun theInnermostConstraintPlacesTheChild() = runComposeSwingTest {
        setContent {
            BorderPanel {
                center {
                    SwingConstraint(BorderLayout.SOUTH) { Label("moved") }
                }
            }
        }

        onNodeWithText("moved").assertLayoutConstraint(BorderLayout.SOUTH)
        assertNull(
            managerPlacing<BorderLayout>("moved").getLayoutComponent(BorderLayout.CENTER),
            "the center region hosts nothing",
        )
    }

    @Test
    fun aNestedContainerKeepsTheConstraintForItselfAndNotItsChildren() = runComposeSwingTest {
        setContent {
            BorderContainer {
                SwingConstraint(BorderLayout.WEST) {
                    // A GridBagLayout rejects a BorderLayout region string, so this composes at all
                    // only because the inner container consumes the constraint for its own placement.
                    GridBagContainer {
                        Label("inner")
                    }
                }
            }
        }

        val inner = onNodeWithText("inner").onParent()
        inner.assertLayoutConstraint(BorderLayout.WEST)
        assertTrue(
            inner.fetch<JPanel>().layout is GridBagLayout,
            "the inner container lays its own children out",
        )
    }

    @Test
    fun removingTheChildReleasesItsRegion() = runComposeSwingTest {
        var present by mutableStateOf(true)
        setContent {
            BorderContainer {
                SwingConstraint(BorderLayout.NORTH) { Label("stays") }
                if (present) SwingConstraint(BorderLayout.SOUTH) { Label("goes") }
            }
        }
        val stays = onNodeWithText("stays")
        stays.onParent().onChildren().assertCountEquals(2)

        present = false
        awaitIdle()

        onNodeWithText("goes").assertDoesNotExist()
        stays.onParent().onChildren().assertCountEquals(1)
        assertNull(
            managerPlacing<BorderLayout>("stays").getLayoutComponent(BorderLayout.SOUTH),
            "the removed child's region is released",
        )
        stays.assertLayoutConstraint(BorderLayout.NORTH)
    }

    @Test
    fun reorderingChildrenKeepsEachOneInItsOwnRegion() = runComposeSwingTest {
        var order by mutableStateOf(listOf("first", "second"))
        setContent {
            BorderContainer {
                order.forEach { text ->
                    key(text) {
                        val region = if (text == "first") BorderLayout.WEST else BorderLayout.EAST
                        SwingConstraint(region) { Label(text) }
                    }
                }
            }
        }

        order = order.reversed()
        awaitIdle()

        onNodeWithText("first").assertLayoutConstraint(BorderLayout.WEST)
        onNodeWithText("second").assertLayoutConstraint(BorderLayout.EAST)
        // Each child kept its region while the reorder rewrote the component array.
        val panel = onNodeWithText("first").onParent()
        panel.onChildAt(0).assertTextEquals("second")
        panel.onChildAt(1).assertTextEquals("first")
    }

    @Test
    fun aManagerThatTakesNoConstraintsIgnoresTheDeclaredValue() = runComposeSwingTest {
        // `Container.addImpl` hands a constraint to a plain LayoutManager only when it is a String, and
        // a FlowLayout discards even that. The component is placed by index either way, which is what
        // `flowPanel.add(child, MosaicCell(...))` does in plain Swing.
        setContent {
            FlowContainer {
                SwingConstraint(MosaicCell(row = 1, column = 1)) { Label("free") }
            }
        }

        val panel = onNodeWithText("free").onParent()
        panel.onChildren().assertCountEquals(1)
        panel.onChildAt(0).assertTextEquals("free")
    }

    @Test
    fun aManagerRejectsAConstraintItDoesNotUnderstandWithItsOwnMessage() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    GridBagContainer {
                        SwingConstraint(BorderLayout.CENTER) { Label("wrong") }
                    }
                }
            }

        assertTrue(
            "GridBagConstraint" in failure.message.orEmpty(),
            "the layout manager's own message reaches the caller: ${failure.message}",
        )
    }

    /**
     * The layout manager of the container holding the component reading [text]. A manager is the
     * authority on what no matcher reads back: which cell of its own a child occupies, and which
     * region holds no child at all.
     */
    private inline fun <reified L : LayoutManager> ComposeSwingTest.managerPlacing(text: String): L =
        onNodeWithText(text).onParent().fetch<Container>().layout as L

    /** The placements [MosaicPanel] offers its callers. */
    private interface MosaicScope {
        fun cell(
            row: Int,
            column: Int,
            content: @Composable () -> Unit,
        )
    }

    /** Collects one composition's cell declarations, in declaration order. */
    private class MosaicScopeImpl : MosaicScope {
        val cells: MutableMap<MosaicCell, @Composable () -> Unit> = LinkedHashMap()

        override fun cell(
            row: Int,
            column: Int,
            content: @Composable () -> Unit,
        ) {
            cells[MosaicCell(row, column)] = content
        }
    }

    /** A constraint type only a caller's own layout manager would understand. */
    private data class MosaicCell(
        val row: Int,
        val column: Int,
    )

    /** A constrained manager of a kind the library does not wrap, keyed by [MosaicCell]. */
    private class MosaicLayout : LayoutManager2 {
        private val cells = LinkedHashMap<Component, MosaicCell>()

        /** The cell [component] is registered under, or `null` when this manager holds no placement for it. */
        fun cellOf(component: Component): MosaicCell? = cells[component]

        override fun addLayoutComponent(
            comp: Component,
            constraints: Any?,
        ) {
            require(constraints is MosaicCell) { "cannot add to layout: constraints must be a MosaicCell" }
            cells[comp] = constraints
        }

        override fun addLayoutComponent(
            name: String?,
            comp: Component,
        ): Unit = throw IllegalArgumentException("cannot add to layout: constraints must be a MosaicCell")

        override fun removeLayoutComponent(comp: Component) {
            cells.remove(comp)
        }

        override fun preferredLayoutSize(parent: Container): Dimension = CELL

        override fun minimumLayoutSize(parent: Container): Dimension = CELL

        override fun maximumLayoutSize(target: Container): Dimension = CELL

        override fun getLayoutAlignmentX(target: Container): Float = 0f

        override fun getLayoutAlignmentY(target: Container): Float = 0f

        override fun invalidateLayout(target: Container): Unit = Unit

        override fun layoutContainer(parent: Container) {
            for (child in parent.components) {
                val cell = cells[child] ?: continue
                child.setBounds(cell.column * CELL.width, cell.row * CELL.height, CELL.width, CELL.height)
            }
        }

        private companion object {
            val CELL = Dimension(40, 20)
        }
    }

    /**
     * A [BorderLayout] that records the placement of every child registered with it and counts the
     * registrations cleared again, so a test can tell how often a child's placement reaches the layout.
     */
    private class RecordingLayout(
        private val delegate: BorderLayout = BorderLayout(),
    ) : LayoutManager2 by delegate {
        val registered: MutableList<Any?> = mutableListOf()

        var unregistered: Int = 0
            private set

        override fun addLayoutComponent(
            comp: Component,
            constraints: Any?,
        ) {
            registered += constraints
            delegate.addLayoutComponent(comp, constraints)
        }

        override fun removeLayoutComponent(comp: Component) {
            unregistered++
            delegate.removeLayoutComponent(comp)
        }
    }
}
