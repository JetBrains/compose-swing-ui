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
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Color
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
