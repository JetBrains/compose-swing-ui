package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JViewport
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/** How a Foundation container lays its content out as the view a viewport stretches to its extent. */
class ViewportStretchTest {
    @Test
    fun `a stretched view whose policy answers less than its child lays the child out no wider than the extent`() {
        // Diverges from androidx. Its Scroll.kt:452-456 (compose/foundation/foundation/src/commonMain/kotlin/androidx/
        // compose/foundation) measures a scroll container's content with no maximum on the scroll axis only and keeps
        // the cross-axis maximum, and the view is never stretched to the viewport's extent. A JViewport stretches a
        // view that is smaller than the extent, so the view is the extent and the policy offers its child no more than
        // that, where androidx offers it the child's own size on the scroll axis.
        // Answers no size, as an animated size starting from nothing does, and measures its child under its own offer.
        val answersNothing =
            object : MeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints,
                ): MeasureResult {
                    val placeables = measurables.map { it.measure(constraints.copyMaxDimensions()) }
                    return layout(constraints.minWidth, constraints.minHeight) { placeables.forEach { it.place(0, 0) } }
                }

                override fun IntrinsicMeasureScope.minIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int,
                ): Int = 0

                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int,
                ): Int = 0

                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int,
                ): Int = 0

                override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int,
                ): Int = 0
            }
        runComposeSwingTest {
            setContent {
                InViewport {
                    Layout(answersNothing, modifier = SwingModifier.testTag(VIEW)) {
                        Label("", modifier = SwingModifier.testTag(CHILD).preferredSize(WIDE, 40))
                    }
                }
            }

            val extent = onNodeWithTag(VIEWPORT).fetch<JViewport>().extentSize
            assertEquals(extent, onNodeWithTag(VIEW).fetch().size)
            assertEquals(
                Dimension(extent.width, 40),
                onNodeWithTag(CHILD).fetch().size,
                "the child is no wider than the extent, though it prefers $WIDE",
            )
        }
    }

    @Test
    fun `a stretched view fills its children to the viewport's extent`() {
        val views: Map<String, Pair<@Composable () -> Unit, (Dimension) -> Dimension>> =
            mapOf(
                "Column, fillMaxWidth" to
                    Pair(
                        { Column { Label("x", modifier = SwingModifier.testTag(CHILD).fillMaxWidth()) } },
                        { extent -> Dimension(extent.width, -1) },
                    ),
                "Box, fillMaxSize" to
                    Pair(
                        { Box { Label("x", modifier = SwingModifier.testTag(CHILD).fillMaxSize()) } },
                        { extent -> extent },
                    ),
                "Row, fillMaxWidth(0.5f)" to
                    Pair(
                        { Row { Label("x", modifier = SwingModifier.testTag(CHILD).fillMaxWidth(0.5f)) } },
                        { extent -> Dimension((extent.width * 0.5f).roundToInt(), -1) },
                    ),
                "Box, aspectRatio(2f)" to
                    Pair(
                        { Box { Label("x", modifier = SwingModifier.testTag(CHILD).aspectRatio(2f)) } },
                        { extent -> Dimension(extent.width, (extent.width / 2f).roundToInt()) },
                    ),
                // Column answers this child's width at the child's own height, so the viewport stretches the view.
                "Column, aspectRatio(16f / 9f) preferring wider than the viewport" to
                    Pair(
                        {
                            Column {
                                Label(
                                    "",
                                    modifier =
                                        SwingModifier
                                            .testTag(CHILD)
                                            .aspectRatio(16f / 9f)
                                            .preferredSize(WIDE, 20),
                                )
                            }
                        },
                        { extent -> Dimension(extent.width, -1) },
                    ),
            )
        val expected = mutableMapOf<String, Dimension>()
        val sizes = mutableMapOf<String, Dimension>()
        for ((name, view) in views) {
            runComposeSwingTest {
                setContent { InViewport { view.first() } }
                val wanted = view.second(onNodeWithTag(VIEWPORT).fetch<JViewport>().extentSize)
                val child = onNodeWithTag(CHILD).fetch().size
                expected[name] = wanted
                sizes[name] = Dimension(child.width, if (wanted.height < 0) -1 else child.height)
            }
        }
        assertEquals(expected, sizes)
    }
}

@Composable
private fun InViewport(view: @Composable () -> Unit) {
    ScrollPane(modifier = SwingModifier.preferredSize(300, 200)) {
        Viewport(modifier = SwingModifier.testTag(VIEWPORT)) { view() }
    }
}

private const val WIDE = 400
private const val VIEWPORT = "viewport"
private const val VIEW = "view"
private const val CHILD = "child"
