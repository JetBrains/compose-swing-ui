package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.LayoutManager
import java.awt.Rectangle
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A [Layout] panel tracks the state its layout and paint read while a node holding it is attached. */
class ConstrainedPanelObservationTest {
    @Test
    fun aPolicyReadStaysObservedAfterTheRecordingContentRootLeaves() =
        runComposeSwingTest {
            val moved = MovablePanel()
            setContent(moved.content)
            awaitIdle()
            val source = moved.mount()
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()
            val contentRoot = panel.setContent {}
            try {
                awaitIdle()
                moved.inOuter = true
                awaitIdle()
                contentRoot.dispose()
                awaitIdle()
                assertTrue(panel.isValid)

                moved.width.intValue = 30
                Snapshot.sendApplyNotifications()

                assertFalse(
                    panel.isValid,
                    "a changed policy read invalidates the panel after its recording root leaves",
                )
                awaitIdle()
                assertEquals(
                    30,
                    panel.preferredSize.width,
                    "the remaining declaration measures the changed read",
                )
            } finally {
                contentRoot.dispose()
                source.dispose()
            }
        }

    @Test
    fun aPanelIsNeitherLaidOutNorPaintedAgainWhenANodeThatRecordsNothingLeaves() =
        runComposeSwingTest {
            val shared = SharedPanel()
            setContent(shared.content)
            awaitIdle()
            shared.panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()
            onNodeWithTag(PANEL_TAG).captureToImage()
            // The declared node attached first, so it records; the root of this content does not.
            val handle = shared.panel.setContent {}
            awaitIdle()

            withRecordedRepaints { repaints ->
                handle.dispose()
                awaitIdle()

                assertEquals(0, repaints.repaintsOf(shared.panel), "the first node still records the paint reads")
                assertEquals(0, repaints.relayoutsOver(shared.panel), "the first node still records the layout reads")
            }
        }

    @Test
    fun aPanelPaintsAgainWhenTheNodeRecordingItsPaintReadsMovesToAnotherComposition() =
        runComposeSwingTest {
            val moved = MovablePanel()
            setContent(moved.content)
            awaitIdle()
            val handle = moved.mount()
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()
            // The declared node attached first and records; the root of this content does not.
            panel.setContent {}
            awaitIdle()
            onNodeWithTag(PANEL_TAG).captureToImage()

            withRecordedRepaints { repaints ->
                moved.inOuter = true
                awaitIdle()

                assertTrue(repaints.repaintsOf(panel) > 0, "the panel paints again, as its recorded reads were dropped")

                onNodeWithTag(PANEL_TAG).captureToImage()
                repaints.forget()
                moved.color = Color.BLUE
                Snapshot.sendApplyNotifications()

                assertTrue(repaints.repaintsOf(panel) > 0, "a paint read made after the move repaints the panel")
            }
            handle.dispose()
        }

    @Test
    fun aPolicyReadIsObservedAgainOnceAPanelMovedToAnotherCompositionIsAttached() =
        runComposeSwingTest {
            val moved = MovablePanel()
            setContent(moved.content)
            awaitIdle()
            val handle = moved.mount()
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()

            moved.inOuter = true
            awaitIdle()
            moved.width.intValue = 30
            Snapshot.sendApplyNotifications()

            assertFalse(panel.isValid, "a policy read changing after the move invalidates the panel")
            handle.dispose()
        }

    @Test
    fun aNestedPanelsPolicyReadStaysObservedAfterTheSourceCompositionIsDisposed() =
        runComposeSwingTest {
            val moved = MovablePanel(nested = true)
            setContent(moved.content)
            awaitIdle()
            val handle = moved.mount()
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()

            moved.inOuter = true
            awaitIdle()
            handle.dispose()
            awaitIdle()
            assertSame(panel, onNodeWithTag(PANEL_TAG).fetch<JComponent>())
            assertTrue(panel.isValid)

            moved.width.intValue = 30
            Snapshot.sendApplyNotifications()

            assertFalse(panel.isValid, "a policy read invalidates the unchanged child in its new composition")
            awaitIdle()
            assertEquals(30, panel.preferredSize.width)
        }

    @Test
    fun aNestedPanelsPaintReadStaysObservedAfterTheSourceCompositionIsDisposed() =
        runComposeSwingTest {
            val moved = MovablePanel(nested = true)
            setContent(moved.content)
            awaitIdle()
            val handle = moved.mount()
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()

            moved.inOuter = true
            awaitIdle()
            handle.dispose()
            awaitIdle()
            onNodeWithTag(PANEL_TAG).captureToImage()

            withRecordedRepaints { repaints ->
                moved.color = Color.BLUE
                Snapshot.sendApplyNotifications()

                assertTrue(
                    repaints.repaintsOf(panel) > 0,
                    "a paint read repaints the unchanged child in its new composition",
                )
                assertEquals(0, repaints.relayoutsOver(panel), "a paint-only change preserves layout")
            }
        }

    @Test
    fun aPolicyReadIsObservedOnceANodeHoldsAPanelThatWasLaidOutWithoutOne() =
        runComposeSwingTest {
            val shared = SharedPanel()
            setContent(shared.content)
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()
            val parent = panel.parent
            shared.declaredFirst = false
            awaitIdle()
            parent.add(panel)
            panel.revalidate()
            awaitIdle()
            assertTrue(panel.isValid, "the panel was laid out while no node held it")

            val handle = panel.setContent {}
            awaitIdle()
            shared.width.intValue = 30
            Snapshot.sendApplyNotifications()

            assertFalse(panel.isValid, "a policy read changing after a node attached invalidates the panel")
            handle.dispose()
        }

    @Test
    fun aStateReadOnlyInTheMinIntrinsicWidthAStockParentAsksSizesThePanelAgain() =
        assertIntrinsicReadFollowed(Dimension(60, 100)) { Dimension(minIntrinsicWidth(100), 100) }

    @Test
    fun aStateReadOnlyInTheMaxIntrinsicWidthAStockParentAsksSizesThePanelAgain() =
        assertIntrinsicReadFollowed(Dimension(60, 100)) { Dimension(maxIntrinsicWidth(100), 100) }

    @Test
    fun aStateReadOnlyInTheMinIntrinsicHeightAStockParentAsksSizesThePanelAgain() =
        assertIntrinsicReadFollowed(Dimension(100, 60)) { Dimension(100, minIntrinsicHeight(100)) }

    @Test
    fun aStateReadOnlyInTheMaxIntrinsicHeightAStockParentAsksSizesThePanelAgain() =
        assertIntrinsicReadFollowed(Dimension(100, 60)) { Dimension(100, maxIntrinsicHeight(100)) }

    @Test
    fun aStateReadOnlyInTheHeightAtTheWidthABoxLayoutStretchesThePanelToSizesItAgain() =
        assertGrantedWidthReadFollowed(
            swingParentSlots(emptyList()).getValue("BoxLayout Y_AXIS"),
            initial = 80,
            extent = 60,
            expected = 120,
        )

    @Test
    fun aStateReadOnlyInTheHeightAtTheWidthAGridBagLayoutFillsThePanelToSizesItAgain() =
        assertGrantedWidthReadFollowed(
            swingParentSlots(emptyList()).getValue("GridBagLayout fill HORIZONTAL"),
            initial = 80,
            extent = 60,
            expected = 120,
        )

    @Test
    fun aStateReadOnlyInTheLeastHeightAtTheWidthAShortGridBagLayoutFillsThePanelToSizesItAgain() =
        assertGrantedWidthReadFollowed(
            // Short of the panel's preferred height, the cell grants its least height.
            { width, subject ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.north().preferredSize(width, 70)) {
                        subject(SwingModifier.item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
                    }
                }
            },
            initial = 40,
            extent = 60,
            expected = 60,
        )

    @Test
    fun aStateReadOnlyInThePreferredHeightAloneAtTheWidthABoxLayoutStretchesThePanelToSizesItAgain() =
        assertSplitReadsFollowed(
            swingParentSlots(emptyList()).getValue("BoxLayout Y_AXIS"),
            initial = 80,
            changed = { _, preferred -> preferred.intValue = 120 },
            expected = 120,
        )

    @Test
    fun aStateReadOnlyInTheLeastHeightAloneAtTheWidthABoxLayoutStretchesThePanelToLeavesItAsItWas() =
        assertSplitReadsFollowed(
            swingParentSlots(emptyList()).getValue("BoxLayout Y_AXIS"),
            initial = 80,
            changed = { least, _ -> least.intValue = 60 },
            expected = 80,
        )

    @Test
    fun aStateReadOnlyInTheLeastHeightAloneAtTheWidthAShortGridBagLayoutFillsThePanelToSizesItAgain() =
        assertSplitReadsFollowed(
            // Short of the panel's preferred height, the cell grants its least height.
            { width, subject ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.north().preferredSize(width, 70)) {
                        subject(SwingModifier.item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
                    }
                }
            },
            initial = 40,
            changed = { least, _ -> least.intValue = 60 },
            expected = 60,
        )

    @Test
    fun aStateReadOnlyInThePreferredHeightAloneAtTheWidthAShortGridBagLayoutFillsThePanelToLeavesItAsItWas() =
        assertSplitReadsFollowed(
            { width, subject ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.north().preferredSize(width, 70)) {
                        subject(SwingModifier.item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
                    }
                }
            },
            initial = 40,
            changed = { _, preferred -> preferred.intValue = 120 },
            expected = 40,
        )

    @Test
    fun aStateReadOnlyInTheHeightsAPaintChecksAtTheWidthASqueezedBoxLayoutGrantsSizesThePanelAgain() =
        assertGrantedWidthReadFollowed(
            // Between the panel's least and preferred heights, BoxLayout grants all it has, by neither size; short of
            // the least height, that height.
            { width, subject ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    Panel(
                        PanelLayout.Box(BoxLayout.Y_AXIS),
                        modifier = SwingModifier.north().preferredSize(width, 70),
                    ) { subject(SwingModifier) }
                }
            },
            initial = 70,
            extent = 100,
            expected = 100,
            painted = true,
        )

    /**
     * Lays out, in [slot], a panel whose policy reads an extent only at widths other than the one it prefers, where
     * the slot grants it, and which takes that extent as its least height and twice it as its preferred one. Asserts it
     * is [initial] high at the slot's width, painted there where [painted] holds. Then changes the extent from 40 to
     * [extent], and asserts the panel is laid out at the [expected] height with no paint in between.
     */
    private fun assertGrantedWidthReadFollowed(
        slot: Slot,
        initial: Int,
        extent: Int,
        expected: Int,
        painted: Boolean = false,
    ) = runComposeSwingTest {
        val read = mutableIntStateOf(40)
        val policy = GrantedWidthPolicy(read)
        setContent { slot(300) { modifier -> Layout(policy, modifier.testTag("panel")) } }
        val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
        if (painted) settleWithPaint()
        assertEquals(Dimension(300, initial), panel.size, "the panel stands at the slot's width")

        read.intValue = extent
        awaitIdle()

        assertEquals(Dimension(300, expected), panel.size, "a changed read at the granted width sizes the panel again")
    }

    /**
     * As [assertGrantedWidthReadFollowed], for a panel whose least height reads one state and whose preferred height
     * another. Asserts it is [initial] high at the slot's width, then runs [changed] on the two states, 40 and 80 to
     * begin with, and asserts it is [expected] high with no paint in between.
     */
    private fun assertSplitReadsFollowed(
        slot: Slot,
        initial: Int,
        changed: (least: MutableIntState, preferred: MutableIntState) -> Unit,
        expected: Int,
    ) = runComposeSwingTest {
        val least = mutableIntStateOf(40)
        val preferred = mutableIntStateOf(80)
        val policy = SplitHeightsPolicy(least, preferred)
        setContent { slot(300) { modifier -> Layout(policy, modifier.testTag("panel")) } }
        val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
        assertEquals(Dimension(300, initial), panel.size, "the panel stands at the slot's width")

        changed(least, preferred)
        awaitIdle()

        assertEquals(Dimension(300, expected), panel.size, "the panel follows the height its slot lays it out by")
    }

    /**
     * Hosts a [Layout] whose policy reads an extent only in its intrinsic functions under a stock parent that sizes it
     * by [sizeOf], one of the intrinsic functions of its [Constrainable], and never asks its preferred or minimum size.
     * Changes that extent from 40 to 60 and asserts the panel is laid out at [expected].
     */
    private fun assertIntrinsicReadFollowed(
        expected: Dimension,
        sizeOf: Constrainable.() -> Dimension,
    ) = runComposeSwingTest {
        val extent = mutableIntStateOf(40)
        val sizing = ConstrainableSizingLayout(sizeOf)
        val parent = JPanel(sizing)
        setContent {
            SwingNode(factory = { parent }) { Layout(IntrinsicExtentPolicy(extent), SwingModifier.testTag("panel")) }
        }
        val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
        sizing.child = panel
        parent.revalidate()
        awaitIdle()

        extent.intValue = 60
        awaitIdle()

        assertEquals(expected, panel.size, "a changed intrinsic read sizes the panel again")
    }

    /** Sizes its one [child] by [sizeOf] alone, in the layout it runs and for the size it answers its own parent. */
    private class ConstrainableSizingLayout(
        private val sizeOf: Constrainable.() -> Dimension,
    ) : LayoutManager {
        var child: ConstrainedPanel? = null

        override fun addLayoutComponent(
            name: String?,
            comp: Component,
        ) = Unit

        override fun removeLayoutComponent(comp: Component) = Unit

        override fun preferredLayoutSize(parent: Container): Dimension = child?.sizeOf() ?: Dimension()

        override fun minimumLayoutSize(parent: Container): Dimension = child?.sizeOf() ?: Dimension()

        override fun layoutContainer(parent: Container) {
            val panel = child ?: return
            panel.bounds = Rectangle(panel.sizeOf())
        }
    }

    /** Answers [extent] to every intrinsic question, and takes the extent its constraints fix when measured. */
    private class IntrinsicExtentPolicy(
        private val extent: MutableIntState,
    ) : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = extent.intValue

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = extent.intValue

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = extent.intValue

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = extent.intValue
    }

    /**
     * Prefers to be 50 wide, at least 40 and preferably 80 tall there; at any other width, at least [extent] and
     * preferably twice it tall. Takes the extent its constraints fix when measured.
     */
    private class GrantedWidthPolicy(
        private val extent: MutableIntState,
    ) : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = 50

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = 50

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = if (width == 50) 40 else extent.intValue

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = 2 * minIntrinsicHeight(measurables, width)
    }

    /**
     * Prefers to be 50 wide, at least 40 and preferably 80 tall there; at any other width, at least the height [least]
     * holds and preferably the one [preferred] holds. Takes the extent its constraints fix when measured.
     */
    private class SplitHeightsPolicy(
        private val least: MutableIntState,
        private val preferred: MutableIntState,
    ) : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = 50

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = 50

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = if (width == 50) 40 else least.intValue

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = if (width == 50) 80 else preferred.intValue
    }

    /** One [Layout] panel that can leave its declaration. */
    private class SharedPanel {
        val width: MutableIntState = mutableIntStateOf(10)
        private val policy = WidthPolicy(width)
        private val modifier = SwingModifier.panelModifier { Color.RED }
        var declaredFirst by mutableStateOf(true)
        lateinit var panel: JComponent

        val content: @Composable () -> Unit = {
            SwingNode(factory = { JPanel(BorderLayout()) }) {
                if (declaredFirst) Layout(measurePolicy = policy, modifier = modifier)
            }
        }
    }

    /** One [Layout] panel that moves between this composition and the one [mount] makes on a host of its own. */
    private class MovablePanel(
        private val nested: Boolean = false,
    ) {
        val width: MutableIntState = mutableIntStateOf(10)
        private val policy = WidthPolicy(width)
        var color: Color by mutableStateOf(Color.RED)
        private val modifier = SwingModifier.panelModifier { color }
        var inOuter by mutableStateOf(false)
        private val host = JPanel(BorderLayout())
        private lateinit var context: CompositionContext
        private lateinit var movable: @Composable () -> Unit

        val content: @Composable () -> Unit = {
            context = rememberCompositionContext()
            movable =
                remember {
                    movableContentOf {
                        if (nested) {
                            SwingNode(factory = { JPanel(BorderLayout()) }) {
                                Layout(measurePolicy = policy, modifier = modifier)
                            }
                        } else {
                            Layout(measurePolicy = policy, modifier = modifier)
                        }
                    }
                }
            SwingNode(factory = { host })
            if (inOuter) movable()
        }

        fun mount() = host.setContent(parent = context) { if (!inOuter) movable() }
    }

    /** Takes the width it reads as the extent of the panel, 10 tall. */
    private class WidthPolicy(
        private val width: MutableIntState,
    ) : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult = layout(width.intValue, 10) {}
    }

    private companion object {
        const val PANEL_TAG = "panel"

        fun SwingModifier.panelModifier(color: () -> Color): SwingModifier =
            testTag(PANEL_TAG).background(brush = { _, _ -> color() })
    }
}
