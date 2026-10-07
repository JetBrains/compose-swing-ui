package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Proves Foundation's supported parent-layout contracts work without any internal API opt-in. */
@ExtendWith(ComposedPanels::class)
class PublicParentLayoutModifierTest {
    @Test
    fun customParentDataModifiersFoldInTheirDeclarationOrder() {
        runComposeSwingTest {
            var observed: List<String>? = null
            val events = mutableListOf<String>()
            val parentDataProtocol = layoutParentDataProtocol("external test parent data")
            setContent {
                Layout(
                    content = {
                        SizedChild(
                            index = 0,
                            modifier =
                                SwingModifier then
                                    AppendParentData("first", parentDataProtocol) then
                                    AppendParentData("second", parentDataProtocol) then
                                    OffsetLayoutElement("offset", 9, events),
                        )
                    },
                    measurePolicy =
                        MeasurePolicy { measurables, constraints ->
                            observed =
                                (measurables.single().parentData as? List<*>).orEmpty().filterIsInstance<String>()
                            val placeable = measurables.single().measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                    parentDataProtocol = parentDataProtocol,
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                )
            }

            assertEquals(listOf("first", "second"), observed)
            assertTrue(
                events.isNotEmpty() && events.chunked(2).all { it == listOf("offset before", "offset after") },
                "the layout modifier must wrap every measurement in its before/after order",
            )
            assertEquals(listOf(Rectangle(9, 0, CHILD_WIDTH, CHILD_HEIGHT)), childBounds())
        }
    }

    @Test
    fun customLayoutModifiersFormANestedMeasureAndPlacementChain() =
        runSwingTest {
            val events = mutableListOf<String>()
            val layout =
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                    null,
                )
            val panel = composed(ConstrainedPanel(layout))
            val child = FixedSizeChild(width = 10, height = 10)
            panel.add(child)
            layout.declareComponentLayout(
                child,
                null,
                listOf(OffsetLayoutNode("outer", 5, events), OffsetLayoutNode("inner", 7, events)),
            )
            panel.setSize(10, 10)

            panel.doLayout()

            assertEquals(listOf("outer before", "inner before", "inner after", "outer after"), events)
            assertEquals(Rectangle(12, 0, 10, 10), child.bounds)
        }

    @Test
    fun customLayoutModifiersDelegateIntrinsicsThroughChain() =
        runSwingTest {
            var minW = -1
            var maxW = -1
            var minH = -1
            var maxH = -1
            var base = 0

            val outer =
                object : LayoutModifierNode() {
                    override fun MeasureScope.measure(
                        measurable: Measurable,
                        constraints: Constraints,
                    ): MeasureResult {
                        minW = measurable.minIntrinsicWidth(100)
                        maxW = measurable.maxIntrinsicWidth(100)
                        minH = measurable.minIntrinsicHeight(100)
                        maxH = measurable.maxIntrinsicHeight(100)
                        val placeable = measurable.measure(constraints)
                        base = placeable[FirstBaseline]
                        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                }

            val events = mutableListOf<String>()
            val inner = OffsetLayoutNode("inner", 7, events)

            val layout =
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                    null,
                )
            val panel = composed(ConstrainedPanel(layout))
            val child = FixedSizeChild(width = 10, height = 10)
            panel.add(child)
            layout.declareComponentLayout(child, null, listOf(outer, inner))
            panel.setSize(10, 10)

            panel.doLayout()

            assertEquals(10, minW)
            assertEquals(10, maxW)
            assertEquals(10, minH)
            assertEquals(10, maxH)
            assertEquals(AlignmentLine.UNSPECIFIED, base)
        }

    /**
     * A stateful node - an animation node, in `swing-ui-animation` - overrides the four intrinsic hooks
     * to pass straight through rather than inheriting [LayoutModifierNode]'s measure-based default, precisely
     * so an intrinsic query never runs its own `measure` again. Without that override, a validate cycle
     * asking preferred, minimum and layout would run `measure` up to five times over one real pass.
     */
    @Test
    fun aPassThroughLayoutModifierRunsItsOwnMeasureOnlyOnceAcrossAValidateCycle() =
        runSwingTest {
            var measureCount = 0
            val passThrough =
                object : LayoutModifierNode() {
                    override fun MeasureScope.measure(
                        measurable: Measurable,
                        constraints: Constraints,
                    ): MeasureResult {
                        measureCount++
                        val placeable = measurable.measure(constraints)
                        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }

                    override fun IntrinsicMeasureScope.minIntrinsicWidth(
                        measurable: IntrinsicMeasurable,
                        height: Int,
                    ): Int = measurable.minIntrinsicWidth(height)

                    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                        measurable: IntrinsicMeasurable,
                        height: Int,
                    ): Int = measurable.maxIntrinsicWidth(height)

                    override fun IntrinsicMeasureScope.minIntrinsicHeight(
                        measurable: IntrinsicMeasurable,
                        width: Int,
                    ): Int = measurable.minIntrinsicHeight(width)

                    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                        measurable: IntrinsicMeasurable,
                        width: Int,
                    ): Int = measurable.maxIntrinsicHeight(width)
                }

            val layout =
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                    null,
                )
            val panel = composed(ConstrainedPanel(layout))
            val child = FixedSizeChild(width = 10, height = 10)
            panel.add(child)
            layout.declareComponentLayout(child, null, listOf(passThrough))
            panel.setSize(10, 10)

            panel.preferredSize
            panel.minimumSize
            panel.doLayout()

            assertEquals(
                1,
                measureCount,
                "a validate cycle asking preferred, minimum and layout in turn must run a pass-through " +
                    "node's own measure exactly once, from the real layout pass",
            )
        }

    /**
     * A node whose [LayoutModifierNode.shouldAutoInvalidate] is `false` gets no measure from a changed
     * declaration on its own; it must call [LayoutModifierNode.invalidateMeasurement] itself for a change
     * its own measure reads.
     */
    @Test
    fun invalidateMeasurementMeasuresTheNodeAgainForAChangeAutoInvalidationWouldMiss() {
        runComposeSwingTest {
            var size by mutableIntStateOf(10)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier then ReportedSizeElement(size))
                }
            }

            assertEquals(Dimension(10, 10), containerPreferredSize(), "the node must report the size it starts with")

            size = 24
            awaitIdle()

            assertEquals(
                Dimension(24, 24),
                containerPreferredSize(),
                "invalidateMeasurement must measure the node again for a change shouldAutoInvalidate is false for",
            )
        }
    }

    @Test
    fun repeatedWeightUsesTheLastDeclarationOfItsOwnParentDataKey() {
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(width = 180, height = CHILD_HEIGHT)) {
                    SizedChild(0, SwingModifier.weight(1f).weight(2f))
                    SizedChild(1, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 120, CHILD_HEIGHT), Rectangle(120, 0, 60, CHILD_HEIGHT)),
                childBounds(),
                "the later weight must replace the earlier weight without replacing the sibling's declaration",
            )
        }
    }

    @Test
    fun rowParentDataHoistedUnderABoxIsRefusedBeforeTheBoxReceivesIt() {
        val rowWeight = with(RowScopeInstance) { SwingModifier.weight(1f) }

        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent { Box { SizedChild(0, rowWeight) } }
                }
            }

        assertTrue(
            "linear parent data" in failure.message.orEmpty(),
            "the family descriptor should identify the linear declaration under the Box: ${failure.message}",
        )
    }

    @Test
    fun boxParentDataHoistedUnderARowIsRefusedBeforeTheRowReceivesIt() {
        val boxAlignment = with(BoxScopeInstance) { SwingModifier.align(Alignment.Center) }

        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent { Row { SizedChild(0, boxAlignment) } }
                }
            }

        assertTrue(
            "Box parent data" in failure.message.orEmpty(),
            "the family descriptor should identify the Box declaration under the Row: ${failure.message}",
        )
    }

    @Test
    fun aCustomStackTokenHoistedUnderAnotherLayoutIsRefusedBeforeAttachmentOrStatePublication() =
        runComposeSwingTest {
            val stackProtocol = layoutParentDataProtocol("Stack parent data")
            val otherProtocol = layoutParentDataProtocol("other custom parent data")
            var rejectedChild: Component? = null

            val failure =
                assertFailsWith<IllegalStateException> {
                    setContent {
                        Layout(
                            content = {
                                SwingNode(
                                    factory = {
                                        FixedSizeChild(width = CHILD_WIDTH, height = CHILD_HEIGHT).also {
                                            rejectedChild = it
                                        }
                                    },
                                    modifier = SwingModifier then StackParentData(stackProtocol),
                                )
                            },
                            measurePolicy =
                                MeasurePolicy { _, constraints ->
                                    layout(constraints.minWidth, constraints.minHeight) {}
                                },
                            parentDataProtocol = otherProtocol,
                        )
                    }
                }

            val child = checkNotNull(rejectedChild)
            assertTrue("Stack parent data" in failure.message.orEmpty())
            assertEquals(
                0,
                root.componentCount,
                "the refusal must leave no partially attached custom Layout in the host",
            )
            assertEquals(null, child.parent, "the rejected child must not attach or publish layout state")
        }

    private data class AppendParentData(
        private val value: String,
        override val parentProtocol: ParentProtocol,
    ) : ParentDataModifier {
        override val key: Any get() = value

        override val name: String get() = "appendParentData"

        override val declaredValues: Map<String, Any?> get() = mapOf("value" to value)

        override fun modifyParentData(parentData: Any?): Any? = (parentData as? List<*>).orEmpty() + value
    }

    private data class StackParentData(
        override val parentProtocol: ParentProtocol,
    ) : ParentDataModifier {
        override val name: String get() = "stackParentData"

        override fun modifyParentData(parentData: Any?): Any = "stack"
    }

    private data class OffsetLayoutElement(
        private val label: String,
        private val x: Int,
        private val events: MutableList<String>,
    ) : LayoutModifierNodeElement<OffsetLayoutNode>() {
        override fun create(): OffsetLayoutNode = OffsetLayoutNode(label, x, events)

        override fun update(node: OffsetLayoutNode) {
            node.label = label
            node.x = x
            node.events = events
        }
    }

    private class OffsetLayoutNode(
        var label: String,
        var x: Int,
        var events: MutableList<String>,
    ) : LayoutModifierNode() {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            events += "$label before"
            val placeable = measurable.measure(constraints)
            events += "$label after"
            return layout(placeable.width, placeable.height) { placeable.place(x, 0) }
        }
    }

    /**
     * Reports itself at [size] by [size] whatever its content measures, and is measured again only through
     * invalidateMeasurement.
     */
    private data class ReportedSizeElement(
        private val size: Int,
    ) : LayoutModifierNodeElement<ReportedSizeNode>() {
        override fun create(): ReportedSizeNode = ReportedSizeNode(size)

        override fun update(node: ReportedSizeNode) = node.update(size)
    }

    private class ReportedSizeNode(
        private var size: Int,
    ) : LayoutModifierNode() {
        override val shouldAutoInvalidate: Boolean get() = false

        fun update(size: Int) {
            val changed = this.size != size
            this.size = size
            if (changed) invalidateMeasurement()
        }

        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(size, size) { placeable.place(0, 0) }
        }
    }
}
