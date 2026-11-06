package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.TapElement
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.click
import org.jetbrains.compose.swing.test.interaction.performMouseInput
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A [MeasurePolicy] placing a child with a layer, which wraps the child's whole decoration. */
class PolicyPlacementLayerTest {
    companion object {
        /**
         * A faded child is repainted by the container, as the child's area in the container's coordinates, which
         * children faded on one frame paint together. A scaled child repaints itself. The first scaled child makes the
         * container add its glass pane, and Swing then asks the container to repaint the pane's previous bounds, which
         * are empty.
         */
        @JvmStatic
        fun layerSharing(): List<Arguments> {
            val child = Rectangle(0, 0, 40, 20)
            val firstArea = Rectangle(0, 0, 40, 20)
            val secondArea = Rectangle(40, 0, 40, 20)
            val nothing = emptyList<Rectangle>()
            val glassPaneArrival = listOf(Rectangle(0, 0, 0, 0))
            val ownState = Named.of("each block reads a state of its own", LayerSharing(listOf(0, 1)))
            val oneState = Named.of("both blocks read one state", LayerSharing(listOf(0, 0)))
            val oneBlock = Named.of("both children place with one block", LayerSharing(listOf(0, 0), oneBlock = true))
            val scaledChildren =
                listOf(mapOf("child 0" to listOf(child), "child 1" to listOf(child), "container" to glassPaneArrival))
            val fadedChildren =
                listOf(mapOf("child 0" to nothing, "child 1" to nothing, "container" to listOf(firstArea, secondArea)))
            return listOf(
                Arguments.of(
                    Named.of(LayerRead.ScaleX.name, LayerRead.ScaleX),
                    1f,
                    ownState,
                    listOf(
                        mapOf("child 0" to listOf(child), "child 1" to nothing, "container" to glassPaneArrival),
                        mapOf("child 0" to nothing, "child 1" to listOf(child), "container" to nothing),
                    ),
                ),
                Arguments.of(Named.of(LayerRead.ScaleX.name, LayerRead.ScaleX), 1f, oneState, scaledChildren),
                Arguments.of(Named.of(LayerRead.ScaleX.name, LayerRead.ScaleX), 1f, oneBlock, scaledChildren),
                Arguments.of(
                    Named.of(LayerRead.Alpha.name, LayerRead.Alpha),
                    0.8f,
                    ownState,
                    listOf(
                        mapOf("child 0" to nothing, "child 1" to nothing, "container" to listOf(firstArea)),
                        mapOf("child 0" to nothing, "child 1" to nothing, "container" to listOf(secondArea)),
                    ),
                ),
                Arguments.of(Named.of(LayerRead.Alpha.name, LayerRead.Alpha), 0.8f, oneState, fadedChildren),
                Arguments.of(Named.of(LayerRead.Alpha.name, LayerRead.Alpha), 0.8f, oneBlock, fadedChildren),
            )
        }
    }

    @Test
    fun aPolicyLayerFadesTheDecorationTheChildDeclares() =
        runComposeSwingTest {
            setContent { LayeredChild { { alpha = 0.5f } } }

            val image = onNodeWithTag(LAYERED_TAG).captureToImage()
            image.assertPixelColor(Color(255, 0, 0, 128), 20, 10, "the background inside the layer fades")
        }

    /**
     * The layer turns what the child paints about the extent it measured to, the box of its outermost layout
     * modifier: a start padding of 20 leaves the child's own layout bounds the end half of that box, and the
     * background declared before the padding paints at the whole of it. A quarter turn about the box's center puts
     * the background 10 past the layout bounds above, below and before them, and a changed modifier keeps the layer
     * outside it.
     */
    @Test
    fun aPolicyLayerTurnsTheBoxOfTheChildsOutermostLayoutModifier() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            setContent { LayeredChild(paddingStart = 20, color = color) { QuarterTurn } }

            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(10, 10, 10, 0),
                "the turned box paints past the layout bounds above, below and before them",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
            val image = onNodeWithTag(LAYERED_TAG).captureToImage()
            image.assertPixelColor(Color.RED, 5, 2, "the turned background paints above and before the child")
            image.assertPixelColor(Color(0, true), 25, 5, "the turned background leaves the child's top end unpainted")

            color = Color.BLUE
            awaitIdle()

            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(10, 10, 10, 0),
                "the changed background stays inside the layer",
            ) {
                assertIs<Decoratable>(this).decoration.paintOutsets()
            }
            onNodeWithTag(
                LAYERED_TAG,
            ).captureToImage().assertPixelColor(Color.BLUE, 5, 2, "the changed background paints")
        }

    /** A quarter turn of the 40 by 20 child paints 10 past its layout bounds above and below. */
    @Test
    fun aPolicyLayerTurnsTheChildAboutTheExtentItMeasuredAndTakesPaintOutsets() =
        runComposeSwingTest {
            setContent { LayeredChild { QuarterTurn } }

            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(10, 0, 10, 0),
                "the quarter-turned child takes 10 px above and below",
            ) {
                assertIs<Decoratable>(this).decoration.paintOutsets()
            }
            val image = onNodeWithTag(LAYERED_TAG).captureToImage()
            image.assertPixelColor(Color.RED, 20, 2, "the turned child paints above its layout bounds")
            image.assertPixelColor(
                Color(0, true),
                2,
                20,
                "the turned child leaves the ends of its layout bounds unpainted",
            )
        }

    /** An eighth turn of the 40 by 20 child takes the outsets of its turned corners, about the box's center. */
    @Test
    fun aPolicyLayerTurnedAnEighthTakesTheOutsetsOfItsTurnedCorners() =
        runComposeSwingTest {
            setContent { LayeredChild { EighthTurn } }

            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(12, 2, 12, 2),
                "the eighth-turned corners take the outsets",
            ) {
                assertIs<Decoratable>(this).decoration.paintOutsets()
            }
        }

    /**
     * A clipping, doubling layer takes the outsets of its doubled box, not of a shadow past that box: the clip cuts
     * the shadow away before the scale grows the box.
     */
    @Test
    fun aClippingScaledPolicyLayerTakesTheOutsetsOfItsScaledBoxOnly() =
        runComposeSwingTest {
            setContent { LayeredChild(shadowRadius = 8) { ClipAndDouble } }

            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(10, 20, 10, 20),
                "the doubled box takes the outsets, and the shadow is clipped away",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
        }

    @Test
    fun placingTheChildWithoutALayerRemovesTheLayer() =
        runComposeSwingTest {
            var layered by mutableStateOf(true)
            setContent { LayeredChild { if (layered) QuarterTurn else null } }
            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(10, 0, 10, 0),
                "the quarter-turned child takes 10 px above and below",
            ) {
                assertIs<Decoratable>(this).decoration.paintOutsets()
            }

            layered = false
            awaitIdle()

            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(0, 0, 0, 0),
                "the child placed plainly paints within its bounds",
            ) {
                assertIs<Decoratable>(this).decoration.paintOutsets()
            }
            onNodeWithTag(LAYERED_TAG).captureToImage().assertPixelColor(Color.RED, 2, 10)
        }

    /**
     * An eighth turn of a 100 by 100 child: a press where the turn paints a point reaches the child at that point,
     * through its container's overlay, and a press inside only the unturned box misses.
     */
    @Test
    fun aPressWhereAPolicyLayerTurnsTheChildHitsItAtTheUnturnedPoint() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val presses = ArrayList<Point>()
            setWindowContent {
                Box(
                    modifier = SwingModifier.styleable { preferredSize(300, 300) },
                    contentAlignment = Alignment.Center,
                ) {
                    Layout(
                        content = {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag(LAYERED_TAG)
                                        .mouseListener(onMousePressed = { presses += it.point }),
                            ) {}
                        },
                        measurePolicy =
                            MeasurePolicy { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints.fixed(100, 100))
                                layout(placeable.width, placeable.height) {
                                    placeable.placeWithLayer(0, 0) { rotationZ = 45f }
                                }
                            },
                    )
                }
            }
            val node = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(LAYERED_TAG)
            val canvas = node.fetch<JComponent>()
            val outsets = assertIs<Decoratable>(canvas).decoration.paintOutsets()
            val layoutOrigin = Point(outsets.left, outsets.top)
            val turn =
                TransformOrigin.Center.createTransform(Rectangle2D.Double(0.0, 0.0, 100.0, 100.0), 1f, 1f, 45f)
            val painted = turn.transform(Point2D.Double(6.0, 6.0), null)

            node.performMouseInput {
                click(Point(layoutOrigin.x + floor(painted.x).toInt(), layoutOrigin.y + floor(painted.y).toInt()))
            }
            assertEquals(1, presses.size, "a press inside the turned outline must hit")
            val unturned = Point(6 + outsets.left, 6 + outsets.top)
            assertTrue(presses.single().distance(unturned) <= 1.5, "the press lands at $unturned: ${presses.single()}")

            node.performMouseInput { click(Point(layoutOrigin.x + 4, layoutOrigin.y + 4)) }
            assertEquals(1, presses.size, "a press inside only the unturned box must miss")
        }

    /**
     * A child moved, mid-composition, from a policy that places it with a layer to a plain container loses the
     * layer: placed without one there, its own decoration no longer carries it.
     */
    @Test
    fun aChildMovedFromALayeredPolicyToAPlainContainerLosesTheLayer() =
        runComposeSwingTest {
            var underLayeredPolicy by mutableStateOf(true)
            setContent {
                val leaf =
                    remember {
                        movableContentOf {
                            Box(
                                modifier =
                                    SwingModifier
                                        .testTag(LAYERED_TAG)
                                        .background(Brush.of(Color.RED))
                                        .styleable { preferredSize(40, 20) },
                            )
                        }
                    }
                if (underLayeredPolicy) {
                    Layout(
                        content = { leaf() },
                        measurePolicy =
                            MeasurePolicy { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                                layout(placeable.width, placeable.height) {
                                    placeable.placeWithLayer(0, 0) { rotationZ = 90f }
                                }
                            },
                    )
                } else {
                    Box { leaf() }
                }
            }
            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(10, 0, 10, 0),
                "the policy's quarter turn takes 10 px above and below",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }

            underLayeredPolicy = false
            awaitIdle()

            onNodeWithTag(LAYERED_TAG).assertProperty(
                Insets(0, 0, 0, 0),
                "the child moved to a plain container must lose the layer",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
            onNodeWithTag(
                LAYERED_TAG,
            ).captureToImage().assertPixelColor(Color.RED, 2, 10, "the child paints unturned in a plain container")
        }

    /**
     * A right-to-left container's policy placing a narrower child with [Placeable.placeRelativeWithLayer] mirrors
     * the offset from the leading edge, as its plain [Placeable.placeRelative] does, and keeps the layer.
     */
    @Test
    fun aPolicyPlacesRelativelyWithALayerMirroredUnderARightToLeftParent() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag(LAYERED_TAG)
                                    .background(Brush.of(Color.RED))
                                    .styleable { preferredSize(20, 20) },
                        )
                    },
                    measurePolicy =
                        MeasurePolicy { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints.fixed(20, 20))
                            layout(40, 20) {
                                placeable.placeRelativeWithLayer(0, 0) { alpha = 0.5f }
                            }
                        },
                    modifier = ContainerModifier.styleable(containerStyle(40, 20, ComponentOrientation.RIGHT_TO_LEFT)),
                )
            }

            onNodeWithTag(LAYERED_TAG).assertProperty(
                40 - 20,
                "an offset of 0 from the leading edge sits flush with the right edge under a right-to-left parent",
            ) { x }
            val pixel = onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(10, 10)
            assertEquals(128.0, (pixel ushr 24).toDouble(), 1.0, "the layer's alpha still applies once mirrored")
        }

    @Test
    fun placingAChildThatIsNotDecoratableWithALayerFails() =
        runComposeSwingTest {
            var failure: IllegalArgumentException? = null
            setContent {
                Layout(
                    content = { Label("plain") },
                    measurePolicy =
                        MeasurePolicy { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                            layout(placeable.width, placeable.height) {
                                try {
                                    placeable.placeWithLayer(0, 0, layerBlock = QuarterTurn)
                                } catch (thrown: IllegalArgumentException) {
                                    failure = thrown
                                    placeable.place(0, 0)
                                }
                            }
                        },
                )
            }

            val message = assertNotNull(failure, "placing a plain label with a layer must fail").message.orEmpty()
            assertTrue(Decoratable::class.java.name in message, message)
        }

    /** A state read in the block repaints the child, and lays nothing out. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.foundation.layout.PlacementLayerFadeFixtureKt#layerReads")
    internal fun aReadInAPolicyLayerBlockRepaintsTheChildWithoutInvalidating(read: LayerRead) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val value = mutableFloatStateOf(1f)
            val paints = TapElement()
            val block: PlacementLayerScope.() -> Unit = { read.write(this, value.floatValue) }
            setWindowContent {
                LayeredChild(
                    SwingModifier.testTag(CONTAINER_TAG),
                    childModifier = SwingModifier.then(paints),
                ) { block }
            }
            val child = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val container = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            container.paintImmediately(0, 0, container.width, container.height)
            assertTrue(child.dirtyRegion().isEmpty, "the realized child must start painted")
            val painted = paints.node.paints

            if (read.redrawsRecording) {
                value.floatValue = 0.5f
                Snapshot.sendApplyNotifications()
                awaitEventsDelivered()
                assertTrue(paints.node.paints > painted, "the ${read.name} change did not redraw the layer")
            } else {
                assertDirtyRegionsDuring(
                    child,
                    listOf(Rectangle(0, 0, 40, 20)),
                    "a changed ${read.name} read must repaint the child",
                ) {
                    value.floatValue = 0.5f
                    Snapshot.sendApplyNotifications()
                }
            }
            assertTrue(container.isValidUpToTheValidateRoot(), "a layer read must not invalidate anything")
            awaitIdle()
            read.assertPainted(onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(LAYERED_TAG).captureToImage())
        }

    /**
     * A read turning the child from a quarter turn one way to a quarter turn the other repaints it: the turned box
     * stays the same, so only the changed rotation tells the child to repaint.
     */
    @Test
    fun aReadTurningAPolicyLayerTheOtherWayRepaintsTheChild() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val rotation = mutableFloatStateOf(90f)
            val block: PlacementLayerScope.() -> Unit = { rotationZ = rotation.floatValue }
            setWindowContent { LayeredChild(SwingModifier.testTag(CONTAINER_TAG)) { block } }
            val child = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val container = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            container.paintImmediately(0, 0, container.width, container.height)
            assertTrue(child.dirtyRegion().isEmpty, "the realized child must start painted")

            assertDirtyRegionsDuring(
                child,
                listOf(Rectangle(0, 0, 40, 40)),
                "a changed rotation must repaint the child",
            ) {
                rotation.floatValue = -90f
                Snapshot.sendApplyNotifications()
            }
        }

    /**
     * Two children placed with layers whose blocks read a state of their own or one state they share, from [from]. A
     * change of a state to 0.5 makes each component [expected] names for that state ask for exactly the regions it
     * lists, in the component's own coordinates, repaints the children reading the state at the new value, and
     * places nothing again.
     */
    @ParameterizedTest(name = "{0}, {2}")
    @MethodSource("layerSharing")
    internal fun aReadInPolicyLayerBlocksRepaintsOnlyTheChildrenReadingIt(
        read: LayerRead,
        from: Float,
        sharing: LayerSharing,
        expected: List<Map<String, List<Rectangle>>>,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val stateOfChild = sharing.stateOfChild
        val states = List(stateOfChild.max() + 1) { mutableFloatStateOf(from) }
        val blocks: List<PlacementLayerScope.() -> Unit> =
            stateOfChild.map { state -> { read.write(this, states[state].floatValue) } }
        val placedWith = if (sharing.oneBlock) List(blocks.size) { blocks.first() } else blocks
        var placements = 0
        setWindowContent {
            Layout(
                content = {
                    repeat(2) {
                        Box(modifier = SwingModifier.testTag("$LAYERED_TAG$it").background(Brush.of(Color.RED)))
                    }
                },
                measurePolicy =
                    MeasurePolicy { measurables, _ ->
                        val placeables = measurables.map { it.measure(Constraints.fixed(40, 20)) }
                        layout(80, 20) {
                            placements++
                            placeables.forEachIndexed { index, placeable ->
                                placeable.placeWithLayer(40 * index, 0, layerBlock = placedWith[index])
                            }
                        }
                    },
                modifier = SwingModifier.testTag(CONTAINER_TAG),
            )
        }
        val window = onWindowWithTitle(WINDOW_TITLE)
        val children = List(2) { window.onNodeWithTag("$LAYERED_TAG$it").fetch<JComponent>() }
        val container = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
        val components = mapOf("child 0" to children[0], "child 1" to children[1], "container" to container)
        placements = 0

        states.forEachIndexed { changed, state ->
            container.paintImmediately(0, 0, container.width, container.height)
            val reading = children.indices.filter { stateOfChild[it] == changed }
            val asked =
                withRecordedRepaints {
                    state.floatValue = 0.5f
                    Snapshot.sendApplyNotifications()
                    it
                }

            expected[changed].forEach { (name, regions) ->
                assertEquals(
                    regions,
                    asked.dirtyRegionsOf(components.getValue(name)),
                    "a read of state $changed repaints the children reading it: the $name asks for these regions",
                )
            }
            awaitIdle()
            for (index in reading) read.assertPainted(window.onNodeWithTag("$LAYERED_TAG$index").captureToImage())
        }
        assertEquals(0, placements, "a read in a layer block places no child again")
    }

    /** Which state each of two children's layer blocks reads, and whether they place with one block between them. */
    internal data class LayerSharing(
        val stateOfChild: List<Int>,
        val oneBlock: Boolean = false,
    )

    /**
     * A rotation read only in the layer block turns the child's hit-testing with its painting: the container then
     * holds the same paint outsets and finds the same components under a point as a twin composed turned already.
     */
    @Test
    fun aReadTurningAPolicyLayerTurnsHitTestingAsATwinComposedTurned() =
        runComposeSwingTest {
            val rotation = mutableFloatStateOf(0f)
            setContent {
                Layout(
                    content = {
                        TurnedTwin("live") { rotation.floatValue }
                        TurnedTwin("twin") { 45f }
                    },
                    measurePolicy =
                        MeasurePolicy { measurables, _ ->
                            val placeables = measurables.map { it.measure(Constraints.fixed(200, 200)) }
                            layout(400, 200) {
                                placeables.forEachIndexed { index, placeable -> placeable.place(200 * index, 0) }
                            }
                        },
                )
            }
            val unturned = twinGeometry("live")

            rotation.floatValue = 45f
            awaitIdle()

            assertEquals(twinGeometry("twin"), twinGeometry("live"), "the turned child hits as its turned twin")
            assertNotEquals(unturned, twinGeometry("live"), "the turn moves what a point hits")

            rotation.floatValue = 0f
            awaitIdle()

            assertEquals(unturned, twinGeometry("live"), "turned back, the child hits as it did unturned")
        }

    /**
     * A read in the layer block of a child its container left unplaced runs no block; placed again, the child
     * paints at the value the read holds by then.
     */
    @Test
    fun aReadInTheLayerBlockOfAnUnplacedChildRunsNoBlock() =
        runComposeSwingTest {
            val alpha = mutableFloatStateOf(1f)
            var placed by mutableStateOf(true)
            var runs = 0
            val block: PlacementLayerScope.() -> Unit = {
                runs++
                this.alpha = alpha.floatValue
            }
            setContent {
                Layout(
                    content = { Box(modifier = SwingModifier.testTag(LAYERED_TAG).background(Brush.of(Color.RED))) },
                    measurePolicy =
                        MeasurePolicy { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                            layout(40, 20) { if (placed) placeable.placeWithLayer(0, 0, layerBlock = block) }
                        },
                )
            }
            placed = false
            awaitIdle()
            runs = 0

            alpha.floatValue = 0.5f
            awaitIdle()

            assertEquals(0, runs, "the block of an unplaced child does not run")
            placed = true
            awaitIdle()
            val pixel = onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(20, 10)
            assertEquals(128.0, (pixel ushr 24).toDouble(), 1.0, "placed again, the child paints at the new alpha")
        }
}

/**
 * A container of 200 by 200, tagged [tag], placing a child of 60 by 60, tagged with [tag] and a `Child` suffix, at
 * (70, 70) with a layer turning it by [rotation].
 */
@Composable
private fun TurnedTwin(
    tag: String,
    rotation: () -> Float,
) {
    val block: PlacementLayerScope.() -> Unit = remember { { rotationZ = rotation() } }
    Layout(
        content = {
            Layout(
                measurePolicy = MeasurePolicy { _, _ -> layout(60, 60) {} },
                modifier = SwingModifier.testTag(tag + "Child"),
            )
        },
        measurePolicy =
            MeasurePolicy { measurables, _ ->
                val placeable = measurables.single().measure(Constraints.fixed(60, 60))
                layout(200, 200) { placeable.placeWithLayer(70, 70, layerBlock = block) }
            },
        modifier = SwingModifier.testTag(tag),
    )
}

/**
 * The size and paint outsets of the [TurnedTwin] tagged [tag] and of its child, and what Swing finds under each point
 * of a 10 px grid over the container's layout bounds: `L` the child, `c` the container, `.` nothing and `o` another
 * component.
 */
private fun ComposeSwingTest.twinGeometry(tag: String): String {
    val container = onNodeWithTag(tag).fetch<JComponent>()
    val child = onNodeWithTag(tag + "Child").fetch<JComponent>()
    val outsets = assertIs<Decoratable>(container).decoration.paintOutsets()
    return buildString {
        val childOutsets = assertIs<Decoratable>(child).decoration.paintOutsets()
        appendLine("${container.size} $outsets ${child.bounds} $childOutsets")
        for (y in 0 until 200 step 10) {
            for (x in 0 until 200 step 10) {
                val found = SwingUtilities.getDeepestComponentAt(container, x + outsets.left, y + outsets.top)
                append(
                    when {
                        found === child -> 'L'
                        found === container -> 'c'
                        found == null -> '.'
                        else -> 'o'
                    },
                )
            }
            appendLine()
        }
    }
}

/**
 * A container placing one child of 40 by 20, tagged [LAYERED_TAG], painting [color] behind its [paddingStart], with
 * the layer [layerOf] names in its placement, or plainly where it names none.
 */
@Composable
private fun LayeredChild(
    modifier: SwingModifier = SwingModifier,
    paddingStart: Int = 0,
    color: Color = Color.RED,
    shadowRadius: Int = 0,
    childModifier: SwingModifier = SwingModifier,
    layerOf: () -> (PlacementLayerScope.() -> Unit)?,
) {
    Layout(
        content = {
            var declared = childModifier.testTag(LAYERED_TAG).background(Brush.of(color))
            if (shadowRadius > 0) declared = declared.shadow(shadowRadius, Color.BLACK)
            Box(modifier = if (paddingStart == 0) declared else declared.padding(start = paddingStart))
        },
        measurePolicy =
            MeasurePolicy { measurables, _ ->
                val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                layout(placeable.width, placeable.height) {
                    val block = layerOf()
                    if (block == null) placeable.place(0, 0) else placeable.placeWithLayer(0, 0, layerBlock = block)
                }
            },
        modifier = modifier,
    )
}

private val QuarterTurn: PlacementLayerScope.() -> Unit = { rotationZ = 90f }

private val EighthTurn: PlacementLayerScope.() -> Unit = { rotationZ = 45f }

private val ClipAndDouble: PlacementLayerScope.() -> Unit = {
    scaleX = 2f
    scaleY = 2f
    clip = true
}

private const val LAYERED_TAG = "layered"
