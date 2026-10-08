package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Dimension
import java.awt.GridBagConstraints
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A container in a `GridBagLayout` cell. Short of the preferred width, `GridBagLayout` lays the cell out by minimum
 * sizes, and lays a cell with no width or no height out at zero by zero.
 */
class GridBagCellTest {
    @Test
    fun aContainerInACellShortOfRoomTakesTheHeightForTheCellWidthWhereItsContentIsFlatAtItsMinimumWidth() {
        // The label has no minimum size, so its ratio has no height at its minimum width.
        val slots: Map<String, Slot> =
            mapOf(
                "GridBagLayout fill HORIZONTAL" to
                    swingParentSlots(emptyList()).getValue("GridBagLayout fill HORIZONTAL"),
                "GridBagLayout fill HORIZONTAL in a panel of fixed size" to { width, subject ->
                    Panel(PanelLayout.Flow()) {
                        Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(width, 600)) {
                            subject(SwingModifier.item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
                        }
                    }
                },
            )
        val placements: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "at a ratio" to { SwingModifier.aspectRatio(16f / 9f) },
                "filling the width at a ratio" to { SwingModifier.fillMaxWidth().aspectRatio(16f / 9f) },
            )
        for ((slotName, slot) in slots) {
            for ((placementName, placement) in placements) {
                runComposeSwingTest {
                    setContent {
                        slot(320) { modifier ->
                            Box(modifier = modifier.testTag("box")) {
                                Label("", modifier = placement().testTag("label").preferredSize(2 * 320, 600))
                            }
                        }
                    }
                    val box = onNodeWithTag("box").fetch<JComponent>()
                    val label = onNodeWithTag("label").fetch<JComponent>()
                    settleWithPaint()
                    val minimumWidth = label.preferredSize.width
                    val expectedMinimumHeight = (minimumWidth * 9f / 16f).roundToInt()
                    val expectedContentHeight = (box.width * 9f / 16f).roundToInt()
                    assertEquals(
                        Dimension(320, expectedMinimumHeight),
                        box.size,
                        "$slotName, $placementName: minimum height uses natural preferred width",
                    )
                    assertEquals(
                        Dimension(box.width, expectedContentHeight),
                        label.size,
                        "$slotName, $placementName: content follows its actual width",
                    )
                }
            }
        }
    }

    @Test
    fun aContainerNotLaidOutYetHasItsMinimumHeightAtItsPreferredWidth() =
        runComposeSwingTest {
            setContent {
                // A parent with no layout manager lays nothing out, so the box holds no width when its preferences are
                // read.
                SwingNode(factory = { JPanel(null) }) {
                    Box(modifier = SwingModifier.testTag("box")) {
                        Label("Preview", modifier = SwingModifier.testTag("label").aspectRatio(16f / 9f))
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            val label = onNodeWithTag("label").fetch<JComponent>()
            val preferredWidth = label.preferredSize.width

            assertEquals(0, box.width, "precondition: the box holds no width")
            assertEquals(
                Dimension(label.minimumSize.width, (preferredWidth * 9f / 16f).roundToInt()),
                box.minimumSize,
                "its minimum height is the ratio's at the width it prefers, as a wrapping stock widget's is",
            )
        }

    @Test
    fun aContainerTheCellZeroedAndThenGrantedItsPreferredWidthUsesMinimumHeightAtNaturalWidth() {
        // The cell has no height in the second step, so GridBagLayout zeroes it before requesting minimum size.
        val steps: List<ConstrainedScope.() -> SwingModifier> =
            listOf(
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(2 * 320, 600) },
                { SwingModifier.preferredSize(320, 0) },
                { SwingModifier.preferredSize(320, 600 / 10) },
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(2 * 320, 600) },
            )
        runComposeSwingTest {
            var step by mutableIntStateOf(0)
            setContent {
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(320, 600)) {
                        Box(
                            modifier =
                                SwingModifier
                                    .item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL)
                                    .testTag("box"),
                        ) {
                            Label("", modifier = steps[step]().testTag("label"))
                        }
                    }
                }
            }
            for (next in 1 until steps.size) {
                step = next
                awaitIdle()
                if (next == 1) {
                    val box = onNodeWithTag("box").fetch<JComponent>()
                    assertEquals(0, box.width, "precondition: the cell zeroes the box")
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            val label = onNodeWithTag("label").fetch<JComponent>()
            val expectedMinimumHeight = (label.preferredSize.width * 9f / 16f).roundToInt()
            assertEquals(Dimension(320, expectedMinimumHeight), box.size, "minimum height uses natural preferred width")
            assertEquals(
                Dimension(box.width, (box.width * 9f / 16f).roundToInt()),
                label.size,
                "content follows the width the cell grants",
            )
        }
    }

    @ParameterizedTest(name = "native={0}")
    @ValueSource(booleans = [false, true])
    fun aContainerTheCellZeroedAndThenLaidOutAtItsPreferredWidthUpdatesAfterParentValidation(native: Boolean) {
        // Short of room, a cell that does not fill its width zeroes the box at its minimum width. Where the box fits,
        // the cell lays it out at the width it prefers, then the parent can query that width's preferred height.
        val steps: List<ConstrainedScope.() -> SwingModifier> =
            listOf(
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(2 * 320, 600) },
                { SwingModifier.preferredSize(320 / 4, 600 / 10) },
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(320 / 2, 600) },
            )
        val nativeStages =
            listOf(NativeWidthStage.Ratio(640), NativeWidthStage.Fixed(80, 60), NativeWidthStage.Ratio(160))
        runComposeSwingTest {
            var step by mutableIntStateOf(0)
            setContent {
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(320, 600)) {
                        val modifier = SwingModifier.item().testTag("box")
                        WidthDependentSubject(native, modifier, nativeStages[step]) {
                            Box(modifier = modifier) {
                                Label("", modifier = steps[step]().testTag("label"))
                            }
                        }
                    }
                }
            }
            onNodeWithTag("box").assertProperty(0, "precondition: the cell zeroes the subject") { width }
            for (next in 1 until steps.size) {
                step = next
                awaitIdle()
            }
            val subject = onNodeWithTag("box").fetch<JComponent>()
            val expected = Dimension(320 / 2, (320 / 2 * 9f / 16f).roundToInt())
            val earlierPreferredWidth = 320 / 4
            val earlierNaturalHeight = (earlierPreferredWidth * 9f / 16f).roundToInt()
            assertEquals(
                Dimension(expected.width, earlierNaturalHeight),
                subject.size,
                "${if (native) "native" else "Foundation"}: first grant uses the earlier width",
            )
            (subject.parent as JComponent).revalidate()
            root.validate()
            awaitIdle()
            assertEquals(expected, subject.size, "explicit parent validation uses the current width")
            if (!native) onNodeWithTag("label").assertProperty(expected) { size }
        }
    }

    @ParameterizedTest(name = "native={0}")
    @ValueSource(booleans = [false, true])
    fun aContainerMovedFromAZeroedCellIntoAFlowLayoutUpdatesAfterParentValidation(native: Boolean) {
        // The cell lays the box out at the cell's width, then zeroes it for having no height before the flow layout.
        runComposeSwingTest {
            var step by mutableIntStateOf(0)
            setContent {
                val subject =
                    remember {
                        movableContentOf<SwingModifier, Int> { modifier, stageIndex ->
                            val tagged = modifier.testTag("box")
                            WidthDependentSubject(native, tagged, movedNativeStages[stageIndex]) {
                                Box(modifier = tagged) {
                                    Label("", modifier = movedFoundationStages[stageIndex]().testTag("label"))
                                }
                            }
                        }
                    }
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(320, 600)) {
                        if (step < 2) {
                            subject(
                                SwingModifier.item(
                                    weightx = 1.0,
                                    weighty = 1.0,
                                    fill = GridBagConstraints.HORIZONTAL,
                                ),
                                step,
                            )
                        }
                    }
                    Panel(PanelLayout.Flow()) { if (step >= 2) subject(SwingModifier, step) }
                }
            }
            var zeroed: JComponent? = null
            for (next in 1 until movedFoundationStages.size) {
                step = next
                awaitIdle()
                if (next == 1) {
                    zeroed = onNodeWithTag("box").fetch<JComponent>()
                    assertEquals(0, zeroed.width, "precondition: the cell zeroes the subject")
                }
            }
            val component = onNodeWithTag("box").fetch<JComponent>()
            assertSame(zeroed, component, "the same subject moves from GridBag to Flow")
            assertPreferredWidthStages(component, native)
            component.parent.revalidate()
            root.validate()
            awaitIdle()
            val expected = Dimension(320 / 2, (320 / 2 * 9f / 16f).roundToInt())
            onNodeWithTag("box").assertProperty(expected, "the parent validates at the preferred width") { size }
            if (!native) onNodeWithTag("label").assertProperty(expected) { size }
        }
    }
}

@Composable
private fun WidthDependentSubject(
    native: Boolean,
    modifier: SwingModifier,
    stage: NativeWidthStage,
    foundation: @Composable () -> Unit,
) {
    if (native) {
        SwingNode(
            factory = { NativeWidthDependentComponent(stage) },
            modifier = modifier,
            update = {
                set(stage) {
                    this.stage = it
                    revalidate()
                }
            },
        )
    } else {
        foundation()
    }
}

private fun assertPreferredWidthStages(
    component: JComponent,
    native: Boolean,
) {
    val earlierPreferredWidth = 320 / 4
    val earlierNaturalHeight = (earlierPreferredWidth * 9f / 16f).roundToInt()
    assertEquals(
        Dimension(320 / 2, earlierNaturalHeight),
        component.size,
        "${if (native) "native" else "Foundation"}: first grant uses the earlier width",
    )
}

private val movedFoundationStages: List<ConstrainedScope.() -> SwingModifier> =
    listOf(
        { SwingModifier.preferredSize(320 / 4, 600 / 10) },
        { SwingModifier.preferredSize(320 / 4, 0) },
        { SwingModifier.preferredSize(320 / 4, 600 / 10) },
        { SwingModifier.aspectRatio(16f / 9f).preferredSize(320 / 2, 600) },
    )

private val movedNativeStages =
    listOf(
        NativeWidthStage.Fixed(80, 60),
        NativeWidthStage.Fixed(80, 0),
        NativeWidthStage.Fixed(80, 60),
        NativeWidthStage.Ratio(160),
    )

private sealed interface NativeWidthStage {
    data class Fixed(
        val width: Int,
        val height: Int,
    ) : NativeWidthStage

    data class Ratio(
        val naturalWidth: Int,
    ) : NativeWidthStage
}

private class NativeWidthDependentComponent(
    var stage: NativeWidthStage = NativeWidthStage.Ratio(640),
) : JComponent() {
    override fun getPreferredSize(): Dimension =
        when (val current = stage) {
            is NativeWidthStage.Fixed -> {
                Dimension(current.width, current.height)
            }

            is NativeWidthStage.Ratio -> {
                val width = current.naturalWidth
                val heldWidth = this.width.takeIf { it > 0 } ?: current.naturalWidth
                Dimension(width, ratioHeight(heldWidth))
            }
        }

    override fun getMinimumSize(): Dimension =
        Dimension(
            0,
            when (val current = stage) {
                is NativeWidthStage.Fixed -> current.height
                is NativeWidthStage.Ratio -> ratioHeight(current.naturalWidth)
            },
        )

    private fun ratioHeight(width: Int): Int = (width * 9f / 16f).roundToInt()
}
