package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordedRepaints
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.DrawModifierNode
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.decoration
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `LayoutModifierNode.layoutBoxInParent`, read as a third party reads it, from a pass-through layout node. */
internal class LayoutBoxInParentTest {
    @ParameterizedTest
    @MethodSource("boxCases")
    fun aChildReturnsTheBoxItsPaintOutsetsLeave(case: BoxCase) =
        runComposeSwingTest {
            val reading = BoxReading()
            setContent {
                Stage(reading) {
                    if (case.shadow > 0) {
                        Box(SwingModifier.child(case, reading.reader()))
                    } else {
                        Label("child", SwingModifier.child(case, reading.reader()))
                    }
                }
            }

            onNodeWithTag(STAGE).captureToImage()

            assertEquals(case.expected, reading.box, case.name)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placementSequences")
    fun aChildReturnsNullWhileTheContainerLeavesItUnplaced(placements: List<Boolean>) =
        runComposeSwingTest {
            val reading = BoxReading()
            var placed by mutableStateOf(placements.first())
            setContent {
                Layout(
                    content = {
                        Label("child", SwingModifier.testTag(CHILD).preferredSize(30, 20).then(reading.reader()))
                    },
                    modifier = SwingModifier.testTag(STAGE).preferredSize(60, 40).decoration(reading.element),
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(60, 40) { if (placed) placeable.place(10, 5) }
                    },
                )
            }

            val stage = onNodeWithTag(STAGE).fetch<JComponent>()
            placements.forEachIndexed { step, isPlaced ->
                placed = isPlaced
                awaitIdle()
                val paintsBefore = reading.paints
                onNodeWithTag(STAGE).captureToImage()
                assertTrue(reading.paints > paintsBefore, "step $step: the container painted and its draw node read")
                reading.node()
                onNodeWithTag(CHILD).assertProperty(
                    stage,
                    "step $step: the child stays in the Foundation container",
                ) { parent }
                assertEquals(
                    Rectangle(10, 5, 30, 20).takeIf { isPlaced },
                    reading.box,
                    "step $step, placed=$isPlaced",
                )
            }
        }

    @Test
    fun aNodeWhoseComponentLeftTheFoundationContainerReturnsNull() =
        runComposeSwingTest {
            val reading = BoxReading()
            setContent {
                Stage(reading) {
                    Label("child", SwingModifier.testTag(CHILD).preferredSize(30, 20).then(reading.reader()))
                }
            }
            val child = onNodeWithTag(CHILD).fetch<JComponent>()
            val node = reading.node()
            assertEquals(child.bounds, node.layoutBoxInParent(), "under the container")

            val stock = JPanel()
            stock.add(child)

            assertEquals(stock, child.parent, "the component now stands in a parent that is no Foundation container")
            assertNull(node.layoutBoxInParent())
        }

    @Test
    fun readingAContainersAlignmentLinesLeavesTheBoxesOfItsChildren() =
        runComposeSwingTest {
            val reading = BoxReading()
            setContent {
                Box(SwingModifier.testTag(STAGE).decoration(reading.element)) {
                    Label(
                        "child",
                        SwingModifier
                            .testTag(CHILD)
                            .preferredSize(40, 30)
                            .emptyBorder(8)
                            .paintOutsets(PaintOutsets.FullInsets)
                            .then(reading.reader()),
                    )
                }
            }
            val stage = onNodeWithTag(STAGE).fetch<ConstrainedPanel>()
            val placed = reading.node().layoutBoxInParent()
            assertEquals(Rectangle(0, 0, 24, 14), placed, "as placed")

            repeat(2) { stage.alignmentLines }

            assertEquals(
                placed,
                reading.node().layoutBoxInParent(),
                "a placement run to read alignment lines places nothing",
            )
        }

    @Test
    fun aParentReadingTheLinesOfAContainerThatPlacedAgainLeavesTheBoxesOfItsChildren() =
        runComposeSwingTest {
            val reading = BoxReading()
            var innerTick by mutableIntStateOf(0)
            var outerTick by mutableIntStateOf(0)
            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Label(
                                    "child",
                                    SwingModifier
                                        .testTag(CHILD)
                                        .preferredSize(40, 30)
                                        .emptyBorder(8)
                                        .paintOutsets(PaintOutsets.FullInsets)
                                        .then(reading.reader()),
                                )
                            },
                            modifier = SwingModifier.testTag(STAGE).decoration(reading.element),
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(placeable.width, placeable.height) {
                                    innerTick
                                    placeable.place(0, 0)
                                }
                            },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width, placeable.height) {
                            outerTick
                            placeable.place(0, 0)
                            placeable[FirstBaseline]
                        }
                    },
                )
            }
            val placed = reading.node().layoutBoxInParent()
            assertEquals(Rectangle(8, 8, 24, 14), placed, "as placed")

            innerTick = 1
            awaitIdle()
            outerTick = 1
            awaitIdle()

            assertEquals(placed, reading.node().layoutBoxInParent(), "the parent only read the lines of a placed box")
        }

    // The stage has no shadow, which would grow a repaint request by its reach and hide one that is too small.
    // For (3, 6) and (6, 3), the union of the old and new boxes does not cover the child's bounds.
    @ParameterizedTest(name = "from {0} to {1}, decorated {2}")
    @MethodSource("unchangedBoundsCases")
    fun aChildDeclaredWithAnotherValueAtUnchangedBoundsAsksToRepaintItsFullBounds(
        from: Int,
        to: Int,
        decorated: Boolean,
        expected: Rectangle,
    ) = runComposeSwingTest {
        val reading = BoxReading()
        var value by mutableStateOf(PaintOutsets(from))
        setContent {
            Stage(reading, shadowed = false) {
                val child =
                    SwingModifier
                        .testTag(CHILD)
                        .preferredSize(40, 30)
                        .emptyBorder(8)
                        .paintOutsets(value)
                        .then(reading.reader())
                if (decorated) {
                    Box(child.background(Color.WHITE, RectangleShape)) { Label("child") }
                } else {
                    Label("child", child)
                }
            }
        }
        val child = onNodeWithTag(CHILD).fetch<JComponent>()
        val bounds = child.bounds

        val asked =
            withRecordedRepaints {
                value = PaintOutsets(to)
                awaitIdle()
                it
            }

        onNodeWithTag(CHILD).assertProperty(bounds, "the child stays where it was, so Swing repaints nothing for it") {
            this.bounds
        }
        assertEquals(
            expected,
            reading.node().layoutBoxInParent(),
        )
        assertTrue(
            Rectangle(child.size) in asked.dirtyRegionsOf(child),
            "a guide at an edge of either box runs the child's whole width, past both boxes: ${asked.requests}",
        )
    }

    @Test
    fun aPlacementThatEndsOnTheBoxThePreviousOneLeftAsksForNoRepaint() =
        runComposeSwingTest {
            val reading = BoxReading()
            var placements = 0
            var tick by mutableIntStateOf(0)
            setContent {
                Layout(
                    content = {
                        Label(
                            "child",
                            SwingModifier
                                .testTag(CHILD)
                                .emptyBorder(8)
                                .paintOutsets(PaintOutsets.FullInsets)
                                .then(reading.reader()),
                        )
                    },
                    modifier = SwingModifier.testTag(STAGE).preferredSize(120, 80).decoration(reading.element),
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(120, 80) {
                            placements++
                            tick
                            placeable.place(10, 5)
                        }
                    },
                )
            }
            val stage = onNodeWithTag(STAGE).fetch<JComponent>()
            val child = onNodeWithTag(CHILD).fetch<JComponent>()
            val placed = placements

            val asked =
                withRecordedRepaints {
                    tick = 1
                    awaitIdle()
                    it
                }

            assertEquals(placed + 1, placements, "the placement ran again")
            assertEquals(emptyList(), asked.dirtyRegionsOf(stage), "it ended on the box it ended on before")
            assertFalse(asked.repaintsWhole(child), "nor does the child repaint itself: ${asked.requests}")
        }

    // The container grows with its shadow, so Swing repaints it whole for its own growth; a smaller one is for a child.
    @Test
    fun aPlacementThatEndsOnTheBoxLeftBeforeTheContainersOutsetsGrewAsksForNoRepaintOfTheChild() =
        runComposeSwingTest {
            val reading = BoxReading()
            var radius by mutableIntStateOf(4)
            setContent {
                Box(SwingModifier.preferredSize(160, 100)) {
                    Layout(
                        content = {
                            Label(
                                "child",
                                SwingModifier
                                    .testTag(CHILD)
                                    .emptyBorder(8)
                                    .paintOutsets(PaintOutsets.FullInsets)
                                    .then(reading.reader()),
                            )
                        },
                        modifier =
                            SwingModifier
                                .testTag(STAGE)
                                .preferredSize(120, 80)
                                .shadow(radius, Color.BLACK)
                                .decoration(reading.element),
                        measurePolicy = { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints())
                            layout(120, 80) { placeable.place(10, 5) }
                        },
                    )
                }
            }
            val stage = onNodeWithTag(STAGE).fetch<JComponent>()
            val child = onNodeWithTag(CHILD).fetch<JComponent>()
            val childBefore = child.bounds
            val stageBefore = stage.size

            val asked =
                withRecordedRepaints {
                    radius = 8
                    awaitIdle()
                    it
                }

            onNodeWithTag(CHILD).assertProperty(
                true,
                "the container's origin moved and the child moved with it",
            ) { x > childBefore.x }
            assertEquals(
                emptyList(),
                asked.dirtyRegionsOf(stage).filter { it.width < stageBefore.width || it.height < stageBefore.height },
                "the container asked for a repaint smaller than itself, for the child",
            )
            assertFalse(asked.repaintsWhole(child), "the child did not repaint itself: ${asked.requests}")
        }

    @Test
    fun aDetachedNodeThrows() =
        runComposeSwingTest {
            val reading = BoxReading()
            var declared by mutableStateOf(true)
            setContent {
                Stage(reading) {
                    Label(
                        "child",
                        SwingModifier
                            .testTag(CHILD)
                            .preferredSize(30, 20)
                            .then(if (declared) reading.reader() else SwingModifier),
                    )
                }
            }
            val node = reading.node()
            assertEquals(Rectangle(59, 44, 30, 20), node.layoutBoxInParent(), "attached")

            declared = false
            awaitIdle()

            assertFailsWith<IllegalStateException> { node.layoutBoxInParent() }
        }

    @Test
    fun aReadWhileTheContainerPlacesItsChildrenThrows() =
        runComposeSwingTest {
            val reading = BoxReading()
            setContent {
                Stage(reading) {
                    Label(
                        "child",
                        SwingModifier
                            .testTag(CHILD)
                            .preferredSize(30, 20)
                            .then(reading.reader(readsWhilePlacing = true)),
                    )
                }
            }

            onNodeWithTag(STAGE).captureToImage()

            val reads = reading.node().placementReads
            assertTrue(reads.isNotEmpty(), "the node placed its content")
            reads.forEach { assertIs<IllegalStateException>(it.exceptionOrNull(), "a read made while placing: $it") }
        }

    companion object {
        @JvmStatic
        fun boxCases(): Stream<Arguments> =
            Stream
                .of(
                    BoxCase(
                        "a stock child without paint outsets has its bounds for its box",
                        expected = Rectangle(59, 44, 30, 20),
                        size = Dimension(30, 20),
                    ),
                    BoxCase(
                        "a node declared before FullInsets returns the part of the border the value names",
                        expected = Rectangle(62, 47, 24, 14),
                        size = Dimension(40, 30),
                        border = 8,
                        outsets = PaintOutsets.FullInsets,
                    ),
                    BoxCase(
                        "a node declared after FullInsets returns the part of the border the value names",
                        expected = Rectangle(62, 47, 24, 14),
                        size = Dimension(40, 30),
                        border = 8,
                        outsets = PaintOutsets.FullInsets,
                        readerAfterOutsets = true,
                    ),
                    BoxCase(
                        "a shadow's reach is not in the box",
                        expected = Rectangle(59, 44, 30, 20),
                        shadow = 4,
                        size = Dimension(30, 20),
                    ),
                    BoxCase(
                        "FullInsets names the border past the shadow, which the box already leaves out",
                        expected = Rectangle(62, 47, 24, 14),
                        shadow = 4,
                        size = Dimension(40, 30),
                        border = 8,
                        outsets = PaintOutsets.FullInsets,
                    ),
                    BoxCase(
                        "None names none of the shadow's reach, so the box is the whole bounds",
                        expected = Rectangle(45, 30, 58, 48),
                        shadow = 4,
                        size = Dimension(30, 20),
                        outsets = PaintOutsets.None,
                    ),
                    BoxCase(
                        "a child placed past the container's edge keeps its bounds once the container grew its outsets",
                        expected = Rectangle(14, 44, 30, 20),
                        size = Dimension(30, 20),
                        offsetX = -90,
                    ),
                ).map { Arguments.of(Named.of(it.name, it)) }

        @JvmStatic
        fun placementSequences(): Stream<Arguments> =
            Stream.of(
                Arguments.of(Named.of("placed, unplaced, placed again", listOf(true, false, true))),
                Arguments.of(Named.of("never placed, then placed", listOf(false, true))),
            )

        @JvmStatic
        fun unchangedBoundsCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0, 8, false, Rectangle(48, 33, 24, 14)),
                Arguments.of(3, 6, false, Rectangle(46, 31, 28, 18)),
                Arguments.of(6, 3, false, Rectangle(43, 28, 34, 24)),
                Arguments.of(3, 6, true, Rectangle(46, 31, 28, 18)),
            )
    }
}

/** The [expected] box is in stage coordinates. */
internal data class BoxCase(
    val name: String,
    val expected: Rectangle,
    val shadow: Int = 0,
    val size: Dimension? = null,
    val border: Int = 0,
    val offsetX: Int = 0,
    val outsets: PaintOutsets? = null,
    val readerAfterOutsets: Boolean = false,
)

context(_: ConstrainedScope)
private fun SwingModifier.child(
    case: BoxCase,
    reader: SwingModifier,
): SwingModifier {
    val beforeShadow = if (case.readerAfterOutsets) SwingModifier else reader
    val afterOutsets = if (case.readerAfterOutsets) reader else SwingModifier
    return this
        .testTag(CHILD)
        .then(case.size?.let { SwingModifier.preferredSize(it.width, it.height) } ?: SwingModifier)
        .then(if (case.border > 0) SwingModifier.emptyBorder(case.border) else SwingModifier)
        .then(if (case.offsetX != 0) SwingModifier.offset(case.offsetX, 0) else SwingModifier)
        .then(beforeShadow)
        .then(if (case.shadow > 0) SwingModifier.shadow(case.shadow, Color.BLACK) else SwingModifier)
        .then(case.outsets?.let { SwingModifier.paintOutsets(it) } ?: SwingModifier)
        .then(afterOutsets)
}

private fun RecordedRepaints.repaintsWhole(child: JComponent): Boolean =
    dirtyRegionsOf(child).any { it.contains(Rectangle(child.size)) }

private const val STAGE = "stage"
private const val CHILD = "child"

/** A [shadowed] stage's children lie past its layout origin, and a repaint it gets grows by the shadow's reach. */
@Composable
private fun Stage(
    reading: BoxReading,
    shadowed: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(SwingModifier.preferredSize(160, 100)) {
        Box(
            SwingModifier
                .testTag(STAGE)
                .preferredSize(120, 80)
                .then(if (shadowed) SwingModifier.shadow(4, Color.BLACK) else SwingModifier)
                .decoration(reading.element),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** The box the reading node on the child returned when a draw node on the container last painted. */
private class BoxReading {
    var box: Rectangle? = null
    var paints = 0
    var attached: BoxReaderNode? = null

    fun node(): BoxReaderNode = checkNotNull(attached) { "no reading node is attached" }

    fun reader(readsWhilePlacing: Boolean = false): SwingModifier =
        SwingModifier then BoxReaderElement(this, readsWhilePlacing)

    val element: SwingModifier.NodeElement<Container, ReadingNode> =
        object : SwingModifier.NodeElement<Container, ReadingNode>() {
            override val additive: Boolean get() = true

            override val targetType: Class<Container> get() = Container::class.java

            override fun create(): ReadingNode = ReadingNode(this@BoxReading)

            override fun update(node: ReadingNode) = Unit

            override fun equals(other: Any?): Boolean = other === this

            override fun hashCode(): Int = System.identityHashCode(this)
        }
}

private class ReadingNode(
    private val reading: BoxReading,
) : DrawModifierNode<Container>() {
    override fun ContentDrawScope.draw() {
        reading.paints++
        reading.box = reading.attached?.layoutBoxInParent()
        drawContent()
    }
}

private data class BoxReaderElement(
    private val reading: BoxReading,
    private val readsWhilePlacing: Boolean,
) : LayoutModifierNodeElement<BoxReaderNode>() {
    override fun create(): BoxReaderNode = BoxReaderNode(reading, readsWhilePlacing)

    override fun update(node: BoxReaderNode) = Unit
}

private class BoxReaderNode(
    private val reading: BoxReading,
    private val readsWhilePlacing: Boolean,
) : LayoutModifierNode() {
    val placementReads = ArrayList<Result<Rectangle?>>()

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (readsWhilePlacing) placementReads += runCatching { layoutBoxInParent() }
            placeable.place(0, 0)
        }
    }

    override fun onAttach() {
        reading.attached = this
    }

    override fun onDetach() {
        reading.attached = null
    }
}
