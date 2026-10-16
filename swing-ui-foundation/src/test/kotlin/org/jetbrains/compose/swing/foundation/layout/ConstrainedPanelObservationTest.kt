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
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.Border
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.setContent as setSwingContent

/** A [Layout] panel tracks the state its layout and paint read while a node holding it is attached. */
class ConstrainedPanelObservationTest {
    @Test
    fun aStateReadInATopLevelChildsMeasureMeasuresItAgain() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val panel = hostedPanel()
            panel.setContent {
                Box(
                    modifier =
                        SwingModifier.wrapContentSize(Alignment.TopStart).testTag("child").layout { measurable, _ ->
                            val placeable = measurable.measure(Constraints(width.intValue, width.intValue, 10, 10))
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                )
            }
            awaitIdle()

            width.intValue = 30
            awaitIdle()

            assertEquals(30, onNodeWithTag("child").fetch().width, "a changed measure read measures the child again")
        }

    @Test
    fun aStateReadInATopLevelChildsPlacementPlacesItAgain() =
        runComposeSwingTest {
            val x = mutableIntStateOf(0)
            val panel = hostedPanel()
            panel.setContent {
                Box(
                    modifier =
                        SwingModifier.wrapContentSize(Alignment.TopStart).testTag("child").layout { measurable, _ ->
                            val placeable = measurable.measure(Constraints(10, 10, 10, 10))
                            layout(placeable.width, placeable.height) { placeable.place(x.intValue, 0) }
                        },
                )
            }
            awaitIdle()
            val before = onNodeWithTag("child").fetch().x

            x.intValue = 7
            awaitIdle()

            assertEquals(
                before + 7,
                onNodeWithTag("child").fetch().x,
                "a changed placement read places the child again",
            )
        }

    @Test
    fun aTopLevelOffsetChangePlacesTheChildAgain() =
        runComposeSwingTest {
            var x by mutableIntStateOf(0)
            val panel = hostedPanel()
            panel.setContent { Box(modifier = SwingModifier.testTag("child").offset(x = x)) }
            awaitIdle()
            val before = onNodeWithTag("child").fetch().x

            x = 7
            awaitIdle()

            assertEquals(before + 7, onNodeWithTag("child").fetch().x, "a changed offset places the child again")
        }

    @Test
    fun aStateReadInThePanelsPolicyMeasuresItAgain() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val panel = hostedPanel(WidthPolicy(width))
            panel.setContent { Box(modifier = SwingModifier.testTag("child")) }
            awaitIdle()

            width.intValue = 30
            awaitIdle()

            assertEquals(
                Rectangle(0, 0, 30, 10),
                onNodeWithTag("child").fetch().bounds,
                "a changed policy read measures the panel again",
            )
        }

    @Test
    fun aPanelLaidOutBeforeItHostsContentTracksItsPolicyOnceItDoes() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val panel = hostedPanel(WidthPolicy(width))
            awaitIdle()
            panel.setSwingContent {}
            awaitIdle()

            width.intValue = 30
            awaitIdle()

            assertEquals(
                30,
                panel.preferredSize.width,
                "the policy reports its changed read once the panel hosts content",
            )
        }

    @Test
    fun disposingTheContentStopsTrackingThePanelsReads() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val panel = hostedPanel(WidthPolicy(width))
            val content = panel.setContent { Box() }
            awaitIdle()
            width.intValue = 20
            awaitIdle()
            assertEquals(20, panel.preferredSize.width, "the policy reports its changed read while the content is set")

            content.dispose()
            awaitIdle()
            width.intValue = 30
            awaitIdle()

            assertTrue(panel.isValid, "a read after the content is disposed invalidates nothing")
            assertEquals(20, panel.preferredSize.width, "and the panel keeps the report it had")
        }

    @Test
    fun aLayoutPanelKeepsTrackingAfterTheContentItHostsIsDisposed() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val policy = WidthPolicy(width)
            setContent {
                Column(modifier = SwingModifier.preferredSize(300, 200)) {
                    Layout(policy, modifier = SwingModifier.testTag("panel"))
                }
            }
            awaitIdle()
            val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
            panel.setContent { Box() }.dispose()
            awaitIdle()

            width.intValue = 30
            awaitIdle()

            assertEquals(30, panel.width, "the node Layout declared still tracks the policy's reads")
        }

    @Test
    fun aLayoutPanelHostingContentKeepsTrackingAfterLayoutReleasesIt() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val policy = WidthPolicy(width)
            var declared by mutableStateOf(true)
            val host = JPanel(BorderLayout())
            setContent {
                Column(modifier = SwingModifier.preferredSize(300, 200)) {
                    if (declared) Layout(policy, modifier = SwingModifier.testTag("panel"))
                    SwingNode(factory = { host }, modifier = SwingModifier.preferredSize(300, 100))
                }
            }
            awaitIdle()
            val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
            val content = panel.setContent { Box(modifier = SwingModifier.testTag("child")) }
            awaitIdle()
            declared = false
            awaitIdle()
            host.add(panel)
            host.revalidate()
            awaitIdle()

            width.intValue = 30
            awaitIdle()

            assertEquals(
                Rectangle(0, 0, 30, 10),
                onNodeWithTag("child").fetch().bounds,
                "the node its content is rooted at still tracks the policy's reads",
            )
            content.dispose()
        }

    @Test
    fun aPanelHostingContentKeepsTrackingAfterEveryNodeDeclaringItIsReleased() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val panel = ConstrainedPanel(WidthPolicy(width))
            var inRow by mutableStateOf(false)
            var declared by mutableStateOf(true)
            val host = JPanel(BorderLayout())
            setContent {
                val declaration = remember { movableContentOf { SwingNode(factory = { panel }) } }
                Column(modifier = SwingModifier.preferredSize(300, 200)) {
                    if (declared) {
                        if (inRow) Row { declaration() } else Box { declaration() }
                    }
                    SwingNode(factory = { host }, modifier = SwingModifier.preferredSize(300, 100))
                }
            }
            awaitIdle()
            val content = panel.setContent { Box(modifier = SwingModifier.testTag("child")) }
            awaitIdle()
            inRow = true
            awaitIdle()
            declared = false
            awaitIdle()
            host.add(panel)
            host.revalidate()
            awaitIdle()

            width.intValue = 30
            awaitIdle()

            assertEquals(
                Rectangle(0, 0, 30, 10),
                onNodeWithTag("child").fetch().bounds,
                "the node its content is rooted at still tracks the policy's reads",
            )
            content.dispose()
        }

    @Test
    fun aPanelIsNeitherLaidOutNorPaintedAgainWhenANodeThatRecordsNothingLeaves() =
        runComposeSwingTest {
            val shared = SharedPanel()
            setContent(shared.content)
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()
            onNodeWithTag(PANEL_TAG).captureToImage()
            // The declared node attached first, so it records; the root of this content does not.
            val handle = panel.setSwingContent {}
            awaitIdle()

            withRecordedRepaints { repaints ->
                handle.dispose()
                awaitIdle()

                assertEquals(0, repaints.repaintsOf(panel), "the first node still records the paint reads")
                assertEquals(0, repaints.relayoutsOver(panel), "the first node still records the layout reads")
            }
        }

    @Test
    fun movedPanelKeepsAttachedPolicyReadsObserved() = assertMovedRead(MoveRead("attached policy"))

    @Test
    fun movedPanelKeepsPolicyReadsAfterRecordingRootDisposal() =
        assertMovedRead(MoveRead("policy after recording root disposal", recordingRootLeaves = true))

    @Test
    fun nestedMovedPanelKeepsPolicyReadsAfterSourceDisposal() =
        assertMovedRead(MoveRead("nested policy after disposal", nested = true))

    @Test
    fun nestedMovedPanelKeepsPaintReadsAfterSourceDisposal() =
        assertMovedRead(MoveRead("nested paint after disposal", nested = true, paint = true))

    @Test
    fun movedPanelRecordsPaintReadsAgainAfterMoving() =
        assertMovedRead(MoveRead("paint after recording node moves", paint = true))

    private fun assertMovedRead(scenario: MoveRead) =
        runComposeSwingTest {
            val moved = MovablePanel(scenario.nested)
            setContent(moved.content)
            awaitIdle()
            val source = moved.mount()
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()
            // The declared node records; an extra content root does not.
            val extraRoot =
                if (scenario.recordingRootLeaves || (scenario.paint && !scenario.nested)) {
                    panel.setSwingContent {}.also { awaitIdle() }
                } else {
                    null
                }
            try {
                if (scenario.paint) {
                    assertMovedPaintRead(scenario, moved, panel, source)
                } else {
                    moved.inOuter = true
                    awaitIdle()
                    if (scenario.recordingRootLeaves) {
                        extraRoot?.dispose()
                        awaitIdle()
                        assertTrue(panel.isValid, "${scenario.name}: recording root disposal preserves validity")
                    }
                    if (scenario.nested) assertMovedPanelAttached(panel, source, scenario.name)
                    moved.width.intValue = 30
                    Snapshot.sendApplyNotifications()
                    assertFalse(panel.isValid, "${scenario.name}: changed policy read invalidates")
                    if (scenario.nested || scenario.recordingRootLeaves) {
                        awaitIdle()
                        assertEquals(30, panel.preferredSize.width, scenario.name)
                    }
                }
            } finally {
                if (scenario.recordingRootLeaves) extraRoot?.dispose()
                source.dispose()
            }
        }

    private suspend fun ComposeSwingTest.assertMovedPanelAttached(
        panel: JComponent,
        source: DisposableHandle,
        name: String,
    ) {
        source.dispose()
        awaitIdle()
        assertSame(panel, onNodeWithTag(PANEL_TAG).fetch<JComponent>(), name)
        assertTrue(panel.isValid, name)
    }

    private suspend fun ComposeSwingTest.assertMovedPaintRead(
        scenario: MoveRead,
        moved: MovablePanel,
        panel: JComponent,
        source: DisposableHandle,
    ) {
        if (scenario.nested) {
            moved.inOuter = true
            awaitIdle()
            assertMovedPanelAttached(panel, source, scenario.name)
        }
        onNodeWithTag(PANEL_TAG).captureToImage()
        withRecordedRepaints { repaints ->
            if (!scenario.nested) {
                moved.inOuter = true
                awaitIdle()
                assertTrue(repaints.repaintsOf(panel) > 0, "${scenario.name}: the move drops the recorded paint reads")
                onNodeWithTag(PANEL_TAG).captureToImage()
                repaints.forget()
            }
            moved.color = Color.BLUE
            Snapshot.sendApplyNotifications()
            assertTrue(repaints.repaintsOf(panel) > 0, "${scenario.name}: changed paint read repaints")
            assertEquals(0, repaints.relayoutsOver(panel), "${scenario.name}: paint-only change preserves layout")
        }
    }

    private class MoveRead(
        val name: String,
        val nested: Boolean = false,
        val paint: Boolean = false,
        val recordingRootLeaves: Boolean = false,
    )

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

            val handle = panel.setSwingContent {}
            awaitIdle()
            shared.width.intValue = 30
            Snapshot.sendApplyNotifications()

            assertFalse(panel.isValid, "a policy read changing after a node attached invalidates the panel")
            handle.dispose()
        }

    @Test
    fun aPanelKeepsMeasuringAgainUnderItsDeclarationOnceItMovesOutOfAContentRoot() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val panel = ConstrainedPanel(WidthPolicy(width))
            val first = JPanel(BorderLayout())
            var inOuter by mutableStateOf(false)
            lateinit var context: CompositionContext
            lateinit var declaration: @Composable () -> Unit
            setContent {
                context = rememberCompositionContext()
                declaration = remember { movableContentOf { SwingNode(factory = { panel }) } }
                SwingNode(factory = { first })
                if (inOuter) declaration()
            }
            awaitIdle()
            val firstContent = first.setSwingContent(parent = context) { if (!inOuter) declaration() }
            awaitIdle()
            inOuter = true
            awaitIdle()
            firstContent.dispose()
            awaitIdle()

            width.intValue = 30
            awaitIdle()

            assertEquals(30, panel.preferredSize.width, "a changed policy read is reported under the moved declaration")
        }

    @Test
    fun aPanelKeepsPaintingAgainUnderTheNodeDeclaringItOnceItsContentIsDisposed() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var color by mutableStateOf(Color.RED)
            val panel = ConstrainedPanel()
            panel.border =
                object : Border {
                    override fun paintBorder(
                        c: Component,
                        g: Graphics,
                        x: Int,
                        y: Int,
                        width: Int,
                        height: Int,
                    ) {
                        g.color = color
                        g.drawRect(x, y, width - 1, height - 1)
                    }

                    override fun getBorderInsets(c: Component) = Insets(1, 1, 1, 1)

                    override fun isBorderOpaque() = false
                }
            var declared by mutableStateOf(false)
            lateinit var context: CompositionContext
            setWindowContent {
                context = rememberCompositionContext()
                SwingNode(factory = { JPanel(BorderLayout()) }) { if (declared) SwingNode(factory = { panel }) }
            }
            // Set before any node declares the panel, the content's node is the one the panel paints under.
            val content = panel.setContent(parent = context) {}
            declared = true
            awaitIdle()
            content.dispose()
            awaitIdle()

            color = Color.BLUE
            Snapshot.sendApplyNotifications()

            assertEquals(
                Rectangle(0, 0, panel.width, panel.height),
                panel.dirtyRegion(),
                "a changed paint read repaints the panel",
            )
        }

    @Test
    fun minimumWidthHookKeepsItsStateReadObserved() =
        assertIntrinsicRead("minimum width", Dimension(60, 100)) { Dimension(minIntrinsicWidth(100), 100) }

    @Test
    fun preferredWidthHookKeepsItsStateReadObserved() =
        assertIntrinsicRead("preferred width", Dimension(60, 100)) { Dimension(maxIntrinsicWidth(100), 100) }

    @Test
    fun minimumHeightHookKeepsItsStateReadObserved() =
        assertIntrinsicRead("minimum height", Dimension(100, 60)) { Dimension(100, minIntrinsicHeight(100)) }

    @Test
    fun preferredHeightHookKeepsItsStateReadObserved() =
        assertIntrinsicRead("preferred height", Dimension(100, 60)) { Dimension(100, maxIntrinsicHeight(100)) }

    private fun assertIntrinsicRead(
        name: String,
        expected: Dimension,
        sizeOf: Constrainable.() -> Dimension,
    ) = runComposeSwingTest {
        val extent = mutableIntStateOf(40)
        var child: Constrainable? = null
        val parent =
            JPanel(
                ScriptedStockLayout(
                    sizeOf = { child?.sizeOf() ?: Dimension() },
                ) { panel, _ -> child?.let { panel.bounds = Rectangle(it.sizeOf()) } },
            )
        setContent {
            SwingNode(factory = { parent }) {
                Layout(
                    ScriptedMeasurePolicy(
                        minimumWidth = { extent.intValue },
                        preferredWidth = { extent.intValue },
                        minimumHeight = { extent.intValue },
                        preferredHeight = { extent.intValue },
                    ),
                    SwingModifier.testTag(PANEL_TAG),
                )
            }
        }
        val panel = onNodeWithTag(PANEL_TAG).fetch<ConstrainedPanel>()
        child = panel
        parent.revalidate()
        awaitIdle()
        extent.intValue = 60
        awaitIdle()
        assertEquals(
            expected,
            panel.size,
            "$name: a changed intrinsic read sizes the panel again",
        )
    }

    @Test
    fun heightsReadAtAGrantedWidthFollowOnlyTheStateTheirStockParentConsumes() {
        val slots = swingParentSlots(emptyList())
        val shortGridBag =
            shortSlot(PanelLayout.GridBag) { it.item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL) }
        val squeezedBox = shortSlot(PanelLayout.Box(BoxLayout.Y_AXIS)) { it }
        val box = slots.getValue("BoxLayout Y_AXIS")
        val gridBag = slots.getValue("GridBagLayout fill HORIZONTAL")
        val scenarios =
            listOf(
                HeightRead("BoxLayout shared heights", box, 80, 60, 120, read = Read.Shared),
                HeightRead("GridBag shared heights", gridBag, 80, 60, 120, read = Read.Shared),
                HeightRead(
                    "short GridBag shared heights",
                    shortGridBag,
                    40,
                    60,
                    60,
                    read = Read.SharedAtNaturalWidth,
                ),
                HeightRead("BoxLayout preferred", box, 80, 120, 120, read = Read.Preferred),
                HeightRead("BoxLayout minimum", box, 80, 60, 80),
                HeightRead(
                    "short GridBag minimum",
                    shortGridBag,
                    40,
                    60,
                    40,
                    read = Read.MinimumWithPreferredAtGrantedWidth,
                ),
                HeightRead("short GridBag preferred", shortGridBag, 40, 120, 40, read = Read.Preferred),
                // BoxLayout grants neither intrinsic height here, so establish both observations explicitly.
                HeightRead("squeezed BoxLayout shared heights", squeezedBox, 70, 100, 70, read = Read.Shared),
            )
        for (scenario in scenarios) verifyHeightRead(scenario)
    }

    private fun verifyHeightRead(scenario: HeightRead) {
        runComposeSwingTest {
            val least = mutableIntStateOf(40)
            val preferred = mutableIntStateOf(80)
            val policy =
                ScriptedMeasurePolicy(
                    minimumWidth = { 50 },
                    preferredWidth = { 50 },
                    minimumHeight = {
                        if (it == 50 && scenario.read != Read.SharedAtNaturalWidth) 40 else least.intValue
                    },
                    preferredHeight = {
                        if (scenario.read == Read.Shared ||
                            scenario.read == Read.SharedAtNaturalWidth ||
                            scenario.read == Read.MinimumWithPreferredAtGrantedWidth
                        ) {
                            if (it == 50 && scenario.read != Read.SharedAtNaturalWidth) 80 else 2 * least.intValue
                        } else if (it == 50) {
                            80
                        } else {
                            preferred.intValue
                        }
                    },
                )
            setContent { scenario.slot(300) { modifier -> Layout(policy, modifier.testTag(PANEL_TAG)) } }
            val panel = onNodeWithTag(PANEL_TAG).fetch<ConstrainedPanel>()
            assertEquals(Dimension(300, scenario.initial), panel.size, "${scenario.name}: initial grant")
            observePreferences(panel, scenario.read)
            settleWithPaint()
            assertEquals(Dimension(300, scenario.initial), panel.size, "${scenario.name}: after paint")
            val changedState = if (scenario.read == Read.Preferred) preferred else least
            changedState.intValue = scenario.changed
            awaitIdle()
            assertEquals(
                Dimension(300, scenario.expected),
                panel.size,
                "${scenario.name}: changed state without paint",
            )
            if (scenario.read == Read.MinimumWithPreferredAtGrantedWidth) {
                assertEquals(
                    120,
                    queryPreferredHeight(panel),
                    "${scenario.name}: preferred query at granted width",
                )
                assertEquals(
                    Dimension(300, scenario.expected),
                    panel.size,
                    "${scenario.name}: direct query does not resize grant",
                )
            }
        }
    }

    private fun observePreferences(
        panel: ConstrainedPanel,
        read: Read,
    ) {
        when (read) {
            Read.Shared, Read.SharedAtNaturalWidth -> {
                panel.minimumSize = null
                panel.minimumSize
                panel.preferredSize = null
                panel.preferredSize
            }

            Read.Minimum, Read.MinimumWithPreferredAtGrantedWidth -> {
                panel.minimumSize = null
                panel.minimumSize
            }

            Read.Preferred -> {
                panel.preferredSize = null
                panel.preferredSize
            }
        }
    }

    private fun queryPreferredHeight(panel: ConstrainedPanel): Int {
        panel.preferredSize = null
        return panel.preferredSize.height
    }

    private fun <S : PanelScope> shortSlot(
        layout: PanelLayout<S>,
        modifier: S.(SwingModifier) -> SwingModifier,
    ): Slot =
        { width, subject ->
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                Panel(layout, modifier = SwingModifier.north().preferredSize(width, 70)) {
                    subject(modifier(SwingModifier))
                }
            }
        }

    /** A panel standing in a stock parent that stretches it, built outside any composition. */
    private fun ComposeSwingTest.hostedPanel(policy: MeasurePolicy? = null): ConstrainedPanel {
        val panel = if (policy == null) ConstrainedPanel() else ConstrainedPanel(policy)
        setContent { SwingNode(factory = { JPanel(BorderLayout()).apply { add(panel) } }) }
        return panel
    }

    private enum class Read { Shared, Minimum, Preferred, SharedAtNaturalWidth, MinimumWithPreferredAtGrantedWidth }

    private class HeightRead(
        val name: String,
        val slot: Slot,
        val initial: Int,
        val changed: Int,
        val expected: Int,
        val read: Read = Read.Minimum,
    )

    /** One [Layout] panel that can leave its declaration. */
    private class SharedPanel {
        val width: MutableIntState = mutableIntStateOf(10)
        private val policy = WidthPolicy(width)
        private val modifier = SwingModifier.panelModifier { Color.RED }
        var declaredFirst by mutableStateOf(true)

        val content: @Composable () -> Unit = {
            SwingNode(factory = { JPanel(BorderLayout()) }) {
                if (declaredFirst) Layout(measurePolicy = policy, modifier = modifier)
            }
        }
    }

    /** One [Layout] panel that moves between this composition and the one [mount] makes on a host of its own. */
    private class MovablePanel(
        nested: Boolean = false,
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

        fun mount() = host.setSwingContent(parent = context) { if (!inOuter) movable() }
    }

    /** Takes the width it reads as the extent of the panel, 10 tall. */
    private class WidthPolicy(
        private val width: MutableIntState,
    ) : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            val extent = width.intValue
            val placeables = measurables.map { it.measure(Constraints(extent, extent, 10, 10)) }
            return layout(extent, 10) { placeables.forEach { it.place(0, 0) } }
        }
    }

    private companion object {
        const val PANEL_TAG = "panel"

        fun SwingModifier.panelModifier(color: () -> Color): SwingModifier =
            testTag(PANEL_TAG).background(brush = { _, _ -> color() })
    }
}
