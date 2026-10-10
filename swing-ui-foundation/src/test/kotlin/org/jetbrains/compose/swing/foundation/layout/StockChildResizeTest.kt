package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A stock child whose height follows its width, sized to the width a question asks it at because a layout of its
 * container follows: it is sized with its paint outsets around that width, and its resize asks no container for a
 * layout pass.
 */
class StockChildResizeTest {
    @Test
    fun aDecoratedWrappingTextAreaTakesItsWrappedHeightInsideItsPaintOutsetsInTheValidationThatGrantsItsWidth() {
        val askedBy: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "measured" to { SwingModifier },
                "asked its intrinsic height" to { SwingModifier.height(IntrinsicSize.Max) },
            )
        for ((name, asked) in askedBy) {
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH + 80)
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                        Column(modifier = SwingModifier.center().testTag("column")) {
                            Box(modifier = asked().fillMaxWidth()) {
                                SwingNode(
                                    factory = { DecoratedTextArea(WRAPPING_TEXT) },
                                    modifier = SwingModifier.testTag("text").fillMaxWidth().shadow(16, Color.BLACK),
                                )
                            }
                        }
                    }
                }
                settleWithPaint()
                val wide = onNodeWithTag("text").fetch<JComponent>().height
                val column = onNodeWithTag("column").fetch<JComponent>()
                withRecordedRepaints { recorder ->
                    width = WIDTH / 2
                    awaitIdle()
                    assertFalse(column in recorder.relayouts, "$name: the column asks for no validation of its own")
                }
                val text = onNodeWithTag("text").fetch<DecoratedTextArea>()

                assertEquals(
                    WIDTH / 2,
                    text.decoration.localLayoutBounds(text).width,
                    "$name: the area's layout bounds fill the narrower width",
                )
                assertWrapped(text, "$name: a single validation")
                assertTrue(text.height > wide, "$name: into more lines than it took at the wider width")
            }
        }
    }

    @Test
    fun wrappingTextAValidContainerResizesToAnswerWhatItPrefersAsksForNoLayoutPass() {
        for ((kind, wrapping) in WrappingTexts) {
            runComposeSwingTest {
                var side by mutableStateOf("Side")
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                        Column(modifier = SwingModifier.center()) {
                            Row(modifier = SwingModifier.testTag("row").height(IntrinsicSize.Max)) {
                                Box(modifier = SwingModifier.testTag("box")) {
                                    wrapping(SwingModifier.testTag("text").fillMaxWidth())
                                }
                                Label(side)
                            }
                        }
                    }
                }
                settleWithPaint()
                val row = onNodeWithTag("row").fetch<JComponent>()
                val box = onNodeWithTag("box").fetch<JComponent>()

                withRecordedRepaints { recorder ->
                    side = "Other"
                    awaitIdle()
                    assertFalse(
                        row in recorder.relayouts || box in recorder.relayouts,
                        "$kind: the row asks the box what it prefers, which resizes the text, and neither asks for " +
                            "a layout pass",
                    )
                }
                assertWrapped(onNodeWithTag("text").fetch<JComponent>(), "$kind: the validation")
            }
        }
    }

    @Test
    fun aWidthQuestionThatResizesWrappingTextInsideAValidContainerAsksForNoLayoutPass() =
        runComposeSwingTest {
            var height by mutableIntStateOf(WIDTH)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                    Column(modifier = SwingModifier.center()) {
                        Row(modifier = SwingModifier.testTag("row").height(height)) {
                            Layout(
                                content = {
                                    Box(modifier = SwingModifier.testTag("box")) {
                                        Label("<html>$WRAPPING_TEXT</html>", modifier = SwingModifier.fillMaxWidth())
                                    }
                                },
                                modifier = SwingModifier.testTag("sideways").fillMaxHeight().width(IntrinsicSize.Max),
                                measurePolicy = SidewaysPolicy,
                            )
                        }
                    }
                }
            }
            settleWithPaint()
            val containers =
                listOf("row", "sideways", "box").associateWith { onNodeWithTag(it).fetch<JComponent>() }

            withRecordedRepaints { recorder ->
                height = WIDTH / 2
                awaitIdle()
                assertEquals(
                    emptyList(),
                    containers.filterValues { it in recorder.relayouts }.keys.toList(),
                    "the row asks the sideways container its width at the new height, whose policy asks the box its " +
                        "height there, which resizes the text, and none asks for a layout pass",
                )
            }
        }

    @Test
    fun askingANestedContainerWhatItPrefersLaysNoStockChildOutAgain() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            val area = ResizeCountingTextArea()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                    Box(modifier = SwingModifier.north()) {
                        Column(modifier = SwingModifier.testTag("column")) {
                            SwingNode(
                                factory = { JPanel(BorderLayout()).apply { add(area) } },
                                modifier = SwingModifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            settleWithPaint()
            width = WIDTH - 60
            awaitIdle()
            area.resizes = 0

            // The box asks the column what it prefers, at a width wider than the one the border layout grants.
            onNodeWithTag("column").fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(0, area.resizes, "the panel holding the area is not laid out again")
        }

    @Test
    fun aStockContainerHoldingTextNotReflowedSinceItsLastResizeLaysTheTextOutOnceAtANarrowerWidth() =
        assertStockContainerLaysTextOutOnceAtANarrowerWidth(
            mapOf<String, Holder>(
                "Box" to { modifier, content -> Box(modifier = modifier) { content() } },
                "Column" to { modifier, content -> Column(modifier = modifier) { content() } },
                "Box holding a Column in a Box" to { modifier, content ->
                    Box(modifier = modifier) { Box { Column { content() } } }
                },
            ),
        )

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 600

        /**
         * Answers its width with the height its one child takes at a width of the container's height, as a container
         * turning its child sideways does, and lays the child out across its own width, within its height.
         */
        val SidewaysPolicy =
            object : MeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints,
                ): MeasureResult {
                    val offer = Constraints(constraints.maxWidth, constraints.maxWidth, 0, constraints.maxHeight)
                    val placeable = measurables.single().measure(offer)
                    return layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                }

                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int,
                ): Int = measurables.single().maxIntrinsicHeight(height)
            }
    }
}

/** A wrapping text area that paints through the decoration its modifier declares, its insets carrying the outsets. */
internal class DecoratedTextArea(
    text: String,
) : JTextArea(text),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    init {
        lineWrap = true
        wrapStyleWord = true
    }

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

    override fun getInsets(): Insets = decoration.insets(super.getInsets())

    override fun getInsets(insets: Insets?): Insets =
        decoration.insets(super.getInsets(insets), insets ?: Insets(0, 0, 0, 0))
}
