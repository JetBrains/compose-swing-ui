package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.maximumSize
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

class RowColumnBaselineExtentTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun baselineExtentFitsBothChildren(parentMeasured: Boolean) =
        runComposeSwingTest {
            setContent {
                val row: @Composable () -> Unit = {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                        BaselineChild(30, SwingModifier.alignByBaseline())
                        BaselineChild(10, SwingModifier.alignByBaseline())
                    }
                }
                if (parentMeasured) {
                    Column(modifier = SwingModifier.styleable { preferredSize(200, 200) }) { row() }
                } else {
                    row()
                }
            }
            assertEquals(
                Dimension(CHILD_WIDTH * 2, 60),
                if (parentMeasured) containerSize() else containerPreferredSize(),
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 20, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
            )
        }

    @Test
    fun policyBaselineUpdatesWithoutGrowingPreferredHeight() =
        runComposeSwingTest {
            var line by mutableIntStateOf(10)
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Layout(
                        measurePolicy = { _, _ -> layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to line)) {} },
                        modifier = SwingModifier.alignByBaseline(),
                    )
                    BaselineChild(30, SwingModifier.alignByBaseline())
                }
            }
            assertEquals(Dimension(CHILD_WIDTH * 2, CHILD_HEIGHT), containerPreferredSize())
            assertEquals(
                listOf(
                    Rectangle(0, 20, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
            )

            line = 20
            awaitIdle()

            assertEquals(
                listOf(
                    Rectangle(0, 10, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
            )
        }

    @ParameterizedTest
    @ValueSource(
        strings = ["weighted", "custom node", "padded", "width cap", "height cap", "weighted cap", "aspect cap"],
    )
    fun intrinsicHeightUsesConstrainedBaselines(case: String) =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    when (case) {
                        "weighted" -> {
                            FlexibleBaselineChild(30, SwingModifier.weight(1f).alignByBaseline())
                            SwingNode(
                                factory = { JPanel() },
                                modifier =
                                    SwingModifier
                                        .weight(1f)
                                        .alignByBaseline()
                                        .styleable({ preferredSize(CHILD_WIDTH, CHILD_HEIGHT) }, RawKind),
                            )
                        }

                        "custom node" -> {
                            BaselineChild(30, SwingModifier.alignByBaseline() then PassThroughNodeElement)
                        }

                        "padded" -> {
                            BaselineChild(30, SwingModifier.padding(top = 5).alignByBaseline())
                        }

                        "width cap", "weighted cap", "height cap" -> {
                            CappedBaselineChild(
                                heightCapped = case == "height cap",
                                modifier =
                                    (if (case == "weighted cap") SwingModifier.weight(1f) else SwingModifier)
                                        .alignByBaseline(),
                            )
                        }

                        "aspect cap" -> {
                            SwingNode(
                                factory = { WidthAsBaselinePanel() },
                                modifier =
                                    SwingModifier
                                        .alignByBaseline()
                                        .aspectRatio(1f)
                                        .styleable({
                                            // The width never binds, so only the cross-axis cap limits
                                            // the aspect ratio.
                                            maximumSize(10_000, 30)
                                        }, RawKind),
                            )
                        }
                    }
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }
            if (case == "weighted") {
                assertEquals(60, containerPreferredSize().height)
            } else {
                val expected =
                    when (case) {
                        "width cap", "weighted cap", "aspect cap" -> Dimension(30 + CHILD_WIDTH, 60)
                        "height cap" -> Dimension(CHILD_WIDTH * 2, 20 + CHILD_HEIGHT - 10)
                        "padded" -> Dimension(CHILD_WIDTH * 2, 5 + 30 + CHILD_HEIGHT - 10)
                        else -> Dimension(CHILD_WIDTH * 2, 60)
                    }
                assertEquals(expected, containerPreferredSize())
            }
        }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun computedAlignmentLinesFitWithinTheContainer(column: Boolean) =
        runComposeSwingTest {
            setContent {
                if (column) {
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                        Child(0, 60, 20, SwingModifier.alignBy { it.measuredWidth })
                        Child(1, 60, 20, SwingModifier.alignBy { 0 })
                    }
                } else {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                        Child(0, 20, 60, SwingModifier.alignBy { it.measuredHeight })
                        Child(1, 20, 60, SwingModifier.alignBy { 0 })
                    }
                }
            }
            assertEquals(if (column) Dimension(120, 40) else Dimension(40, 120), containerPreferredSize())
            assertEquals(
                if (column) {
                    listOf(Rectangle(0, 0, 60, 20), Rectangle(60, 20, 60, 20))
                } else {
                    listOf(Rectangle(0, 0, 20, 60), Rectangle(20, 60, 20, 60))
                },
                childBounds(),
            )
        }
}

@Composable
private fun CappedBaselineChild(
    heightCapped: Boolean,
    modifier: SwingModifier,
) {
    SwingNode(
        factory = {
            if (heightCapped) {
                SizedBaselinePanel(CHILD_WIDTH, 30, 20)
            } else {
                SizedBaselinePanel(30, CHILD_HEIGHT, 30)
            }
        },
        modifier =
            modifier.styleable({
                preferredSize(CHILD_WIDTH, CHILD_HEIGHT)
                maximumSize(
                    if (heightCapped) CHILD_WIDTH else 30,
                    if (heightCapped) 30 else CHILD_HEIGHT,
                )
            }, RawKind),
    )
}

/** A layout node that places its content where it is. */
private data object PassThroughNodeElement : LayoutModifierNodeElement<PassThroughNode>() {
    override fun create(): PassThroughNode = PassThroughNode()

    override fun update(node: PassThroughNode): Unit = Unit
}

private class PassThroughNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private class SizedBaselinePanel(
    private val baselineWidth: Int,
    private val baselineHeight: Int,
    private val reported: Int,
) : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = if (width == baselineWidth && height == baselineHeight) reported else NO_BASELINE
}

private class WidthAsBaselinePanel : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = width
}
