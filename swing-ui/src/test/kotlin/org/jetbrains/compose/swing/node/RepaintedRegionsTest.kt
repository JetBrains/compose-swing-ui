package org.jetbrains.compose.swing.node

import androidx.compose.runtime.AbstractApplier
import org.jetbrains.compose.swing.ServedRepaints
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.RawParentProtocol
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.servedRepaintsOf
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Color
import java.awt.Container
import java.awt.EventQueue
import javax.swing.JLayeredPane
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import kotlin.test.AfterTest

/** An applier over nodes, which is what both the component applier and the menu applier are. */
private typealias NodeApplier = AbstractApplier<SwingNodeHolder<*>>

/**
 * What a change leaves a viewer looking at.
 *
 * Swing serves a change by painting the regions it was asked for and nothing else, so a change that
 * fails to ask for the area it moved leaves the pixels that were there standing. Each case here runs its
 * change through the applier, replays only the regions the change asked for, and compares what that
 * shows with the container painted whole. Nothing here reads a call the applier makes.
 *
 * Children are blocks of flat color, which is what makes a stale pixel visible with no look and feel in
 * play, and every container is given bounds of its own rather than a layout manager, so what each case
 * paints is the change and nothing else.
 *
 * The rectangle the applier asks for on each path is pinned in [SwingApplierTest] and [MenuApplierTest].
 */
class RepaintedRegionsTest {
    private val owners = mutableListOf<TestCompositionOwner>()

    @AfterTest
    fun disposeOwners() {
        owners.forEach { it.dispose() }
        owners.clear()
    }

    @TestFactory
    fun aChangeShowsEveryPixelItMoves(): List<DynamicTest> = listOf(
        case("a removed child") {
            val (panel, applier) = panelOfBlocks()
            served(panel) { applier.pass(applier.root) { remove(1, 1) } }
        },
        case("a child inserted with bounds of its own") {
            val (panel, applier) = panelOfBlocks(blocks = 2)
            val arriving = block(Color.GREEN, x = 80)
            served(panel) { applier.pass(applier.root) { insert(2, SwingNodeHolder(arriving)) } }
        },
        case("a child moved over a sibling it overlaps", minimal = false) {
            val (panel, applier) = panelOfBlocks(blocks = 0)
            applier.pass(applier.root) {
                insert(0, SwingNodeHolder(block(Color.BLUE, x = 0, width = 60)))
                insert(1, SwingNodeHolder(block(Color.RED, x = 20, width = 60)))
            }
            served(panel) { applier.pass(applier.root) { move(0, 2, 1) } }
        },
        case("a cleared composition") {
            val (panel, applier) = panelOfBlocks()
            served(panel) { applier.clearInPass() }
        },
        case("a parked child") {
            val (panel, applier) = panelOfBlocks(blocks = 1)
            val parking = SwingNodeHolder(block(Color.RED, x = 40))
            applier.pass(applier.root) { insert(1, parking) }
            served(panel) { parking.onDeactivate() }
        },
        case("a menu removed from a bar", minimal = false) {
            val (bar, applier) = barOfMenus()
            // The last menu, so no sibling is moved along: a bar lays its menus out in a row, and a
            // component the layout moves repaints the places it left and took only once its tree is
            // realized, which an unrealized composition never is.
            served(bar) { applier.pass(applier.root) { remove(2, 1) } }
        },
        case("a cleared menu bar", minimal = false) {
            val (bar, applier) = barOfMenus()
            served(bar) { applier.clearInPass() }
        },
        case("a child removed from a layered pane", minimal = false) {
            val pane =
                JLayeredPane().apply {
                    background = Color.WHITE
                    isOpaque = true
                    setSize(200, 40)
                }
            val applier = applierOver(pane)
            applier.pass(applier.root) {
                insert(0, SwingNodeHolder(block(Color.BLUE, x = 0)))
                insert(1, SwingNodeHolder(block(Color.RED, x = 40)))
            }
            served(pane) { applier.pass(applier.root) { remove(1, 1) } }
        },
    )

    @TestFactory
    fun aChangeToAChildInARegionShowsEveryPixelItMoves(): List<DynamicTest> = listOf(
        case("a child released from its region") {
            val (panel, applier, host) = panelOfRegions()
            served(panel) { applier.pass(host) { remove(1, 1) } }
        },
        case("a child installed into a region with bounds of its own") {
            val (panel, applier, host) = panelOfRegions(blocks = 2)
            served(panel) { applier.pass(host) { insert(2, regionBlock(Color.GREEN, REGIONS[2])) } }
        },
        case("a child relocated into a region") {
            val (panel, applier, host) = panelOfRegions(blocks = 2)
            val arriving = regionBlock(Color.GREEN, REGIONS[2])
            served(panel) {
                applier.pass(host) {
                    insertBottomUp(2, arriving)
                    insertTopDown(2, arriving)
                }
            }
        },
        case("a child moved to another region") {
            val (panel, applier, host) = panelOfRegions(blocks = 2)
            val moving = host.children[1]
            served(panel) {
                applier.pass(host) {
                    down(moving)
                    moving.applyModifierDiff(regionModifier(REGIONS[2]))
                    up()
                }
            }
        },
        case("a parked child that filled a region") {
            val (panel, _, host) = panelOfRegions()
            served(panel) { host.children[1].onDeactivate() }
        },
        case("a child released from an ordered region") {
            val (panel, applier, host) = panelOfOrderedRegion()
            served(panel) { applier.pass(host) { remove(1, 1) } }
        },
        case("a child moved within an ordered region over a sibling it overlaps", minimal = false) {
            val (panel, applier, host) = panelOfOrderedRegion(width = 60)
            served(panel) { applier.pass(host) { move(0, 2, 1) } }
        },
    )

    /**
     * A case named by the change it makes, run on the event dispatch thread where the applier runs in
     * production.
     *
     * [minimal] says the regions asked for are expected to be exactly the area that came to look
     * different. It is off where the change moves fewer pixels than the child it is about covers - a
     * child lifted over a sibling changes only the overlap, and a menu bar repaints its own border with
     * the menu that left it.
     */
    private fun case(
        name: String,
        minimal: Boolean = true,
        change: () -> ServedRepaints,
    ): DynamicTest = DynamicTest.dynamicTest(name) {
        var failure: Throwable? = null
        EventQueue.invokeAndWait {
            runCatching {
                val served = change()
                served.assertShowsTheTruth(name)
                if (minimal) served.assertIsMinimal(name)
            }.onFailure { failure = it }
        }
        failure?.let { throw it }
    }

    private fun served(
        host: Container,
        change: () -> Unit,
    ): ServedRepaints = servedRepaintsOf(host, change)

    private fun applierOver(root: Container): SwingApplier = SwingApplier(SwingNodeHolder(root).attachedTo(owner()))

    private fun owner(): TestCompositionOwner = TestCompositionOwner.observing().also { owners += it }

    private fun block(
        color: Color,
        x: Int,
        width: Int = 40,
    ): JPanel = JPanel(null).apply {
        background = color
        isOpaque = true
        setBounds(x, 0, width, 20)
    }

    /** A 200x40 white panel holding [blocks] blocks of 40x20 in a row, and the applier over it. */
    private fun panelOfBlocks(blocks: Int = 3): Pair<JPanel, SwingApplier> {
        val panel =
            JPanel(null).apply {
                background = Color.WHITE
                isOpaque = true
                setSize(200, 40)
            }
        val applier = applierOver(panel)
        val colors = listOf(Color.BLUE, Color.RED, Color.GREEN)
        applier.pass(applier.root) {
            repeat(blocks) { index -> insert(index, SwingNodeHolder(block(colors[index], x = index * 40))) }
        }
        return panel to applier
    }

    /**
     * A 200x40 white panel holding each of [blocks] blocks in a region of its own, laid in a row by the
     * attachment that fills the region, and the applier over it. The panel is a node under the
     * composition root, which is held to a single top-level child.
     */
    private fun panelOfRegions(blocks: Int = 3): RegionHost {
        val (panel, applier, host) = regionHost(ChildPlacement.Slots(*REGIONS.toTypedArray()))
        val colors = listOf(Color.BLUE, Color.RED, Color.GREEN)
        applier.pass(host) { repeat(blocks) { index -> insert(index, regionBlock(colors[index], REGIONS[index])) } }
        return RegionHost(panel, applier, host)
    }

    /**
     * A 200x40 white panel holding three blocks of [width] in the one region they fill in order, placed
     * 20 apart so that wider blocks overlap, and the applier over it.
     */
    private fun panelOfOrderedRegion(width: Int = 40): RegionHost {
        val (panel, applier, host) = regionHost(ChildPlacement.OrderedSlots(ORDERED_REGION))
        val colors = listOf(Color.BLUE, Color.RED, Color.GREEN)
        applier.pass(host) {
            colors.forEachIndexed { index, color ->
                val block = SwingNodeHolder(block(color, x = index * 40, width = width))
                block.applyModifierDiff(SwingModifier.slot(RawParentProtocol, ORDERED_REGION, AddedAtIndex))
                insert(index, block)
            }
        }
        return RegionHost(panel, applier, host)
    }

    private fun regionHost(placement: ChildPlacement): RegionHost {
        val root = JPanel(null).apply { setSize(200, 40) }
        val panel =
            JPanel(null).apply {
                background = Color.WHITE
                isOpaque = true
                setSize(200, 40)
            }
        val applier = applierOver(root)
        val host = SwingNodeHolder(panel).apply { childPlacement = placement }
        applier.pass(applier.root) { insert(0, host) }
        return RegionHost(panel, applier, host)
    }

    /** A 40x20 block filling [region], which places it. */
    private fun regionBlock(
        color: Color,
        region: String,
    ): SwingNodeHolder<*> = SwingNodeHolder(block(color, x = 0)).apply { applyModifierDiff(regionModifier(region)) }

    /** A 300x20 menu bar holding three menus, and the applier over it. */
    private fun barOfMenus(): Pair<JMenuBar, MenuApplier> {
        val bar = JMenuBar().apply { setSize(300, 20) }
        val applier = MenuApplier(SwingNodeHolder(bar).attachedTo(owner()))
        applier.pass(applier.root) {
            listOf("File", "Edit", "View").forEachIndexed { index, text ->
                insertBottomUp(index, SwingNodeHolder(JMenu(text)))
            }
        }
        return bar to applier
    }
}

private data class RegionHost(
    val panel: JPanel,
    val applier: SwingApplier,
    val host: SwingNodeHolder<*>,
)

/** The regions of a region-holding panel, each placing its block 40 further along the row. */
private val REGIONS: List<String> = listOf("first", "second", "third")

private const val ORDERED_REGION: String = "item"

/**
 * Fills [region] the way a `SlotAttachment` built on `Container.add` and `Container.remove` does, placing
 * the block at the region's own place in the row.
 */
private fun regionModifier(region: String): SwingModifier {
    val x = REGIONS.indexOf(region) * 40
    return SwingModifier.slot(
        RawParentProtocol,
        region,
        SlotAttachment { host, component, _ ->
            component.setLocation(x, 0)
            host.add(component)
            return@SlotAttachment { host.remove(component) }
        },
    )
}

/** Adds a child at the position it is handed and removes it by identity, as an ordered region's attachment does. */
private val AddedAtIndex =
    SlotAttachment { host, component, index ->
        host.add(component, index)
        return@SlotAttachment { host.remove(component) }
    }

/** Runs [block] against this applier over one change pass, with `current` on [node]. */
internal fun <A : NodeApplier> A.pass(
    node: SwingNodeHolder<*>,
    block: A.() -> Unit,
) {
    onBeginChanges()
    down(node)
    block()
    up()
    onEndChanges()
}

/** Clears this applier the way the runtime does when it disposes a composition: inside a change pass of its own. */
private fun NodeApplier.clearInPass() {
    onBeginChanges()
    clear()
    onEndChanges()
}

/** Hands [instance] over the way the runtime does: top-down as the node is created, bottom-up as it ends. */
internal fun SwingApplier.insert(
    index: Int,
    instance: SwingNodeHolder<*>,
) {
    insertTopDown(index, instance)
    insertBottomUp(index, instance)
}
