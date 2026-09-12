package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Proves Foundation's supported parent-layout contracts work without any internal API opt-in. */
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
                                    OffsetLayoutModifier("offset", 9, events),
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
    fun customLayoutModifiersFormANestedMeasureAndPlacementChain() {
        val events = mutableListOf<String>()
        val layout =
            TestPolicyLayout { measurables, constraints ->
                val placeable = measurables.single().measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        val panel = JPanel(layout)
        val child = FixedSizeChild(width = 10, height = 10)
        panel.add(child)
        layout.declareComponentLayout(
            child,
            null,
            listOf(OffsetLayoutModifier("outer", 5, events), OffsetLayoutModifier("inner", 7, events)),
        )
        panel.setSize(10, 10)

        panel.doLayout()

        assertEquals(listOf("outer before", "inner before", "inner after", "outer after"), events)
        assertEquals(Rectangle(12, 0, 10, 10), child.bounds)
    }

    @Test
    fun customLayoutModifiersDelegateIntrinsicsThroughChain() {
        var minW = -1
        var maxW = -1
        var minH = -1
        var maxH = -1
        var base = -2

        val outer =
            object : LayoutModifier {
                override val name: String get() = "outer"

                override fun MeasureScope.measure(
                    measurable: Measurable,
                    constraints: Constraints,
                ): MeasureResult {
                    minW = measurable.minIntrinsicWidth(100)
                    maxW = measurable.maxIntrinsicWidth(100)
                    minH = measurable.minIntrinsicHeight(100)
                    maxH = measurable.maxIntrinsicHeight(100)
                    base = measurable.baseline(10, 10)
                    val placeable = measurable.measure(constraints)
                    return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
            }

        val events = mutableListOf<String>()
        val inner = OffsetLayoutModifier("inner", 7, events)

        val layout =
            TestPolicyLayout { measurables, constraints ->
                val placeable = measurables.single().measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        val panel = JPanel(layout)
        val child = FixedSizeChild(width = 10, height = 10)
        panel.add(child)
        layout.declareComponentLayout(child, null, listOf(outer, inner))
        panel.setSize(10, 10)

        panel.doLayout()

        assertEquals(10, minW)
        assertEquals(10, maxW)
        assertEquals(10, minH)
        assertEquals(10, maxH)
        assertEquals(-1, base)
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

    private data class OffsetLayoutModifier(
        private val label: String,
        private val x: Int,
        private val events: MutableList<String>,
    ) : LayoutModifier {
        override val name: String get() = "offsetLayout"

        override val declaredValues: Map<String, Any?> get() = mapOf("x" to x)

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
}
