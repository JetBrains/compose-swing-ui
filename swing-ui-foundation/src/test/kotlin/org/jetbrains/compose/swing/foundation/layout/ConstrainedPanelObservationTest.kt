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
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
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
    fun aPanelIsNeitherLaidOutNorPaintedAgainWhenANodeThatRecordsNothingLeaves() =
        runComposeSwingTest {
            val shared = SharedPanel()
            setContent(shared.content)
            awaitIdle()
            val panel = onNodeWithTag(PANEL_TAG).fetch<JComponent>()
            onNodeWithTag(PANEL_TAG).captureToImage()
            // The declared node attached first, so it records; the root of this content does not.
            val handle = panel.setContent {}
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
                    panel.setContent {}.also { awaitIdle() }
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

            val handle = panel.setContent {}
            awaitIdle()
            shared.width.intValue = 30
            Snapshot.sendApplyNotifications()

            assertFalse(panel.isValid, "a policy read changing after a node attached invalidates the panel")
            handle.dispose()
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
