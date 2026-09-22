package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.renderImage
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.swing.modifier.appearance.background as swingBackground

/**
 * A container written as a [MeasurePolicy] rather than as a layout manager. The policy is asked the
 * same questions [Row], [Column] and [Box] are: an extent to name when nothing constrains it, and an
 * extent plus a placement when its container has one.
 *
 * A child inside it is an ordinary child - it declares its own layout modifiers and its own constraint
 * to the policy, and a container nested under it is asked a constrained question like any other.
 */
@ExclusiveWindowSystem
class LayoutTest {
    @Test
    fun aPolicyPlacesEachChildWhereItSays() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                        SizedChild(2)
                    },
                    modifier = containerModifier(200, 300),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                columnRows(0, CHILD_HEIGHT, 2 * CHILD_HEIGHT),
                childBounds(),
                "each child must be placed where the policy placed it",
            )
        }

    @Test
    fun aPolicyGivesLaterChildrenOnlyTheHeightLeftInTheContainer() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    modifier = containerModifier(200, CHILD_HEIGHT),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, 0),
                ),
                childBounds(),
                "a later child must be measured into the height left instead of extending beyond the container",
            )
        }

    @Test
    fun anAspectRatioChildThatHasNoHeightLeftTakesNoHeightFromTheStack() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1, SwingModifier.aspectRatio(2f))
                    },
                    modifier = containerModifier(200, CHILD_HEIGHT),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, -10, 200, 100),
                ),
                childBounds(),
                "an aspect-ratio child that escapes a zero-height offer is seen at no height, and its own extent is " +
                    "centered on that slot",
            )
        }

    @Test
    fun aPolicyNamesWhatTheContainerAsksItsOwnParentFor() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                Dimension(CHILD_WIDTH, 2 * CHILD_HEIGHT),
                containerPreferredSize(),
                "the extent the policy names with nothing to constrain it is what the container prefers",
            )
        }

    @Test
    fun aChildTakesTheExtentThePolicyOffersIt() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints(30, 30, 30, 30))
                        layout(30, 30) { placeable.place(0, 0) }
                    },
                    modifier = containerModifier(200, 300),
                )
            }

            assertEquals(
                listOf(Rectangle(0, 0, 30, 30)),
                childBounds(),
                "a child measured under a fixed extent occupies it rather than the extent it prefers",
            )
        }

    /**
     * The content receiver is [ConstrainedScope], so a child declares the modifiers that stand between
     * the policy's offer and its own measure. The padding narrows what reaches the child and states the
     * child plus its own space as what the policy placed.
     */
    @Test
    fun aChildsOwnLayoutModifiersStandBetweenThePolicyAndTheChild() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.padding(8)) },
                    modifier = containerModifier(200, 300),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                listOf(Rectangle(8, 8, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "a padded child sits inside the space its padding reserved",
            )
        }

    @Test
    fun aPolicyReadsBackWhatAChildDeclaredToIt() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.layoutConstraint("trailing")) },
                    measurePolicy = { measurables, constraints ->
                        val placeables =
                            measurables.map {
                                val placeable = it.measure(Constraints(maxWidth = constraints.maxWidth))
                                placeable to (it.parentData == "trailing")
                            }
                        layout(constraints.maxWidth, CHILD_HEIGHT) {
                            for ((placeable, trailing) in placeables) {
                                placeable.place(if (trailing) constraints.maxWidth - placeable.width else 0, 0)
                            }
                        }
                    },
                    modifier = containerModifier(200, 300),
                )
            }

            assertEquals(
                listOf(Rectangle(150, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "the policy must place the child by the constraint the child declared to it",
            )
        }

    @Test
    fun aPolicyHandedOnALaterPassLaysTheContainerOutAgain() =
        runComposeSwingTest {
            var spaced by mutableStateOf(false)
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    measurePolicy = if (spaced) stackedRows { CHILD_HEIGHT } else stackedRows(),
                    modifier = containerModifier(200, 300),
                )
            }

            assertEquals(columnRows(0, CHILD_HEIGHT), childBounds(), "the container starts under the first policy")

            spaced = true
            awaitIdle()

            assertEquals(
                columnRows(0, 2 * CHILD_HEIGHT),
                childBounds(),
                "and is laid out again by the policy the later pass handed it",
            )
        }

    /**
     * A child the policy stops placing is not drawn and takes no press, as androidx draws no node its parent leaves
     * unplaced, whether or not it is decorated; placed again, it paints and takes the press as before.
     */
    @Test
    fun aChildThePolicyStopsPlacingNoLongerPaintsOrTakesThePress() =
        runComposeSwingTest {
            var placesChildren by mutableStateOf(true)
            setContent {
                Layout(
                    content = {
                        Panel(PanelLayout.Flow(), modifier = SwingModifier.opaque(true).swingBackground(Color.RED)) {}
                        Box(modifier = SwingModifier.background(brush = { _, _ -> Color.BLUE }))
                    },
                    measurePolicy = { measurables, _ ->
                        val placeables = measurables.map { it.measure(Constraints.fixed(CHILD_WIDTH, CHILD_HEIGHT)) }
                        layout(2 * CHILD_WIDTH, CHILD_HEIGHT) {
                            if (placesChildren) {
                                placeables.forEachIndexed { column, placeable ->
                                    placeable.place(column * CHILD_WIDTH, 0)
                                }
                            }
                        }
                    },
                    modifier = containerModifier(2 * CHILD_WIDTH, CHILD_HEIGHT),
                )
            }
            val container = onNodeWithTag(CONTAINER_TAG)
            val placed = container.captureToImage()
            val children = container.fetch<JComponent>().childrenInDeclarationOrder()

            placesChildren = false
            awaitIdle()

            assertImagesPixelPerfect(renderImage(2 * CHILD_WIDTH, CHILD_HEIGHT) {}, container.captureToImage())
            assertEquals(
                listOf(container.fetch<Component>(), container.fetch()),
                children.indices.map {
                    SwingUtilities.getDeepestComponentAt(
                        container.fetch(),
                        it * CHILD_WIDTH + CHILD_WIDTH / 2,
                        CHILD_HEIGHT / 2,
                    )
                },
                "a press where an unplaced child stood must reach the container",
            )

            placesChildren = true
            awaitIdle()

            assertImagesPixelPerfect(placed, container.captureToImage())
            assertEquals(
                children,
                children.indices.map {
                    SwingUtilities.getDeepestComponentAt(
                        container.fetch(),
                        it * CHILD_WIDTH + CHILD_WIDTH / 2,
                        CHILD_HEIGHT / 2,
                    )
                },
                "a child placed again must take the press again",
            )
        }

    /**
     * A child holding the focus when the policy stops placing it gives the focus up, as a Swing component does when it
     * is hidden. Whether or not the window system focuses the window, the window's most recent focus owner names
     * where the focus went.
     */
    @Test
    fun aChildThePolicyStopsPlacingGivesUpTheFocus() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var placesSecond by mutableStateOf(true)
            setWindowContent {
                Layout(
                    content = {
                        TextField("", onValueChange = {}, modifier = SwingModifier.testTag("first"))
                        TextField("", onValueChange = {}, modifier = SwingModifier.testTag("second"))
                    },
                    measurePolicy = { measurables, constraints ->
                        val (first, second) = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
                        layout(first.width + second.width, maxOf(first.height, second.height)) {
                            first.place(0, 0)
                            if (placesSecond) second.place(first.width, 0)
                        }
                    },
                )
            }
            val window = onWindowWithTitle(WINDOW_TITLE)
            val frame = window.fetch<JFrame>()
            val first = window.onNodeWithTag("first").fetch<JComponent>()
            val second = window.onNodeWithTag("second").fetch<JComponent>()
            second.requestFocusInWindow()
            waitUntil { frame.mostRecentFocusOwner === second }

            placesSecond = false
            awaitIdle()

            waitUntil { frame.mostRecentFocusOwner === first }
        }

    /**
     * A child left unplaced in a window that is not focused gives up that window's focus alone: the focused window
     * keeps its focus owner.
     */
    @Test
    fun aChildUnplacedInABackgroundWindowLeavesTheFocusedWindowAlone() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var placesSecond by mutableStateOf(true)
            setContent {
                Window(onCloseRequest = {}, state = WindowState(size = Dimension(300, 200)), title = "background") {
                    Layout(
                        content = {
                            TextField("", onValueChange = {}, modifier = SwingModifier.testTag("first"))
                            TextField("", onValueChange = {}, modifier = SwingModifier.testTag("second"))
                        },
                        measurePolicy = { measurables, constraints ->
                            val (first, second) = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
                            layout(first.width + second.width, maxOf(first.height, second.height)) {
                                first.place(0, 0)
                                if (placesSecond) second.place(first.width, 0)
                            }
                        },
                    )
                }
                Window(onCloseRequest = {}, state = WindowState(size = Dimension(300, 200)), title = "active") {
                    TextField("", onValueChange = {}, modifier = SwingModifier.testTag("other"))
                }
            }
            awaitIdle()
            val background = onWindowWithTitle("background")
            val frame = background.fetch<JFrame>()
            val second = background.onNodeWithTag("second").fetch<JComponent>()
            val other = onWindowWithTitle("active").onNodeWithTag("other").fetch<JComponent>()
            second.requestFocusInWindow()
            waitUntil { frame.mostRecentFocusOwner === second }
            other.requestFocus()
            val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val focused = runCatching { waitUntil(timeout = 5.seconds) { focusManager.focusOwner === other } }.isSuccess
            assumeTrue(focused && !frame.isFocused, "requires a window system that focuses this process's windows")

            placesSecond = false
            awaitIdle()

            assertSame(other, focusManager.focusOwner, "the focused window must keep its focus owner")
            assertSame(
                background.onNodeWithTag("first").fetch<Component>(),
                frame.mostRecentFocusOwner,
                "the background window's focus must move off the unplaced child",
            )
        }

    /** A child the policy leaves unplaced paints nothing, so its paint outsets no longer grows the container. */
    @Test
    fun anUnplacedChildNoLongerGrowsTheContainerByItsPaintOutsets() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            setContent {
                Box {
                    Layout(
                        content = {
                            Box(modifier = SwingModifier.preferredSize(10, 10).shadow(4, Color.BLACK))
                        },
                        measurePolicy = { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints())
                            layout(10, 10) { if (placed) placeable.place(0, 0) }
                        },
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                    )
                }
            }
            val container = onNodeWithTag(CONTAINER_TAG).fetch<Component>()
            assertTrue(container.width > 10, "a placed child's shadow must grow the container")

            placed = false
            awaitIdle()

            assertEquals(Dimension(10, 10), container.size, "the container must shrink back to its own bounds")
        }

    /** A child its own declaration hides stays hidden when the policy places it again. */
    @Test
    fun aChildHiddenByItsDeclarationStaysHiddenWhenPlacedAgain() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.testTag("child").visible(false)) },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                    },
                )
            }

            placed = false
            awaitIdle()
            placed = true
            awaitIdle()

            assertFalse(onNodeWithTag("child").fetch<Component>().isVisible, "the child must stay hidden")
        }

    /** A child that declares visible(false) only while the policy leaves it unplaced is shown once placed again. */
    @Test
    fun aChildHiddenByADeclarationMadeWhileUnplacedIsShownWhenPlacedAgain() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            var shown by mutableStateOf(true)
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.testTag("child").visible(shown)) },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                    },
                )
            }
            val child = onNodeWithTag("child").fetch<Component>()

            placed = false
            awaitIdle()
            shown = false
            awaitIdle()
            placed = true
            awaitIdle()

            assertTrue(child.isVisible, "placing the child again shows it over the declaration made while unplaced")
        }

    /**
     * A child shown by a declaration made while the policy leaves it unplaced paints nothing and takes no press until
     * the policy places it again.
     */
    @Test
    fun aChildShownWhileUnplacedStaysOutOfSightUntilPlacedAgain() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            var visible by mutableStateOf(false)
            setContent {
                Layout(
                    content = {
                        Panel(
                            PanelLayout.Flow(),
                            modifier = SwingModifier.opaque(true).swingBackground(Color.RED).visible(visible),
                        ) {}
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints.fixed(CHILD_WIDTH, CHILD_HEIGHT))
                        layout(CHILD_WIDTH, CHILD_HEIGHT) { if (placed) placeable.place(0, 0) }
                    },
                    modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT),
                )
            }
            val container = onNodeWithTag(CONTAINER_TAG)
            val child = container.fetch<JComponent>().getComponent(0)

            placed = false
            awaitIdle()
            visible = true
            awaitIdle()

            assertFalse(child.isVisible, "the policy must hide again a child it leaves unplaced")
            assertImagesPixelPerfect(renderImage(CHILD_WIDTH, CHILD_HEIGHT) {}, container.captureToImage())
            assertSame(
                container.fetch<Component>(),
                SwingUtilities.getDeepestComponentAt(container.fetch(), CHILD_WIDTH / 2, CHILD_HEIGHT / 2),
                "a press where the unplaced child stands must reach the container",
            )

            placed = true
            awaitIdle()

            assertImagesPixelPerfect(
                renderImage(CHILD_WIDTH, CHILD_HEIGHT) {
                    it.color = Color.RED
                    it.fillRect(0, 0, CHILD_WIDTH, CHILD_HEIGHT)
                },
                container.captureToImage(),
            )
            assertSame(
                child,
                SwingUtilities.getDeepestComponentAt(container.fetch(), CHILD_WIDTH / 2, CHILD_HEIGHT / 2),
                "a press on the placed child must reach it",
            )
        }

    /** A child the policy stops placing is hidden at a zero size, and shown at its placed size once placed again. */
    @Test
    fun aChildPlacedAgainIsShownAtItsPlacedSize() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.testTag("child")) },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                    },
                )
            }
            val child = onNodeWithTag("child").fetch<Component>()

            placed = false
            awaitIdle()

            assertFalse(child.isVisible, "an unplaced child must be hidden")
            assertEquals(Dimension(0, 0), child.size, "an unplaced child must be left at a zero size")

            placed = true
            awaitIdle()

            assertTrue(child.isVisible, "a child declaring no visibility must be shown when placed again")
            assertEquals(Dimension(CHILD_WIDTH, CHILD_HEIGHT), child.size, "and take its placed size")
        }

    /** What else a child declares stands as declared once the policy places it again. */
    @Test
    fun aChildPlacedAgainKeepsWhatElseItDeclares() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.testTag("child").name("declared").enabled(false)) },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                    },
                )
            }

            placed = false
            awaitIdle()
            placed = true
            awaitIdle()

            val child = onNodeWithTag("child").fetch<Component>()
            assertEquals("declared", child.name, "the declared name must stand")
            assertFalse(child.isEnabled, "the declared enabled(false) must stand")
        }

    /** A child left unplaced stays at a zero size and paints nothing, even where a component inside it has a shadow. */
    @Test
    fun anUnplacedChildStaysEmptyWhereItsContentNeedsPaintOutsets() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            setContent {
                Layout(
                    content = {
                        Box(modifier = SwingModifier.testTag("child")) {
                            Box(modifier = SwingModifier.preferredSize(10, 10).shadow(4, Color.BLACK))
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(CHILD_WIDTH, CHILD_HEIGHT) { if (placed) placeable.place(20, 20) }
                    },
                    modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT),
                )
            }
            val child = onNodeWithTag("child").fetch<Component>()

            placed = false
            awaitIdle()

            assertEquals(Dimension(0, 0), child.size, "the content's paint outsets must not grow an unplaced child")
            assertImagesPixelPerfect(
                renderImage(CHILD_WIDTH, CHILD_HEIGHT) {},
                onNodeWithTag(CONTAINER_TAG).captureToImage(),
            )
        }

    /** A child the policy leaves unplaced takes its size again once it moves into a container of its own. */
    @Test
    fun anUnplacedChildMovedToAnotherContainerShowsThere() =
        runComposeSwingTest {
            var inLayout by mutableStateOf(true)
            setContent {
                val child = remember { movableContentOf { SizedChild(0, SwingModifier.testTag("child")) } }
                if (inLayout) {
                    Layout(
                        content = { child() },
                        measurePolicy = { measurables, _ ->
                            measurables.single().measure(Constraints())
                            layout(0, 0) {}
                        },
                    )
                } else {
                    Panel(PanelLayout.Flow()) { child() }
                }
            }
            val unplaced = onNodeWithTag("child").fetch<Component>()

            inLayout = false
            awaitIdle()

            val moved = onNodeWithTag("child").fetch<Component>()
            assertSame(unplaced, moved, "the move must keep the component")
            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                moved.size,
                "the moved child must show in its new container",
            )
            assertTrue(moved.isVisible, "the moved child must be visible in its new container")
        }

    @Test
    fun aLeafLayoutUsesItsMeasurePolicyWithoutComposingChildren() =
        runComposeSwingTest {
            setContent {
                Layout(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    measurePolicy = { _, constraints ->
                        layout(constraints.minWidth, constraints.minHeight) {}
                    },
                )
            }

            val layout = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            assertEquals(0, layout.componentCount, "the leaf overload must not compose an empty child lambda")
            assertEquals(
                Dimension(0, 0),
                layout.preferredSize,
                "the leaf policy must define the panel's preferred size",
            )
        }

    /**
     * A [Layout] answers the constrained question its own parent asks, so a container nested inside one
     * is measured against the extent the policy offered rather than against the extent it prefers.
     *
     * The offer is a ceiling with no floor, which is what makes the reading a measure rather than a
     * placement: a row handed a fixed extent would occupy it either way, because its own bounds are
     * written by the pass that places it. What only a measure settles is the row's weighted child,
     * which takes the width the row was granted.
     */
    @Test
    fun aContainerNestedInsideALayoutIsAskedTheConstrainedQuestion() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        Row {
                            SizedChild(0, SwingModifier.weight(1f))
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable =
                            measurables.single().measure(
                                Constraints(maxWidth = 30, maxHeight = 30),
                            )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                    modifier = containerModifier(200, 300),
                )
            }

            assertEquals(
                listOf(Rectangle(0, 0, 30, 30)),
                childBounds(),
                "the nested row must settle for the ceiling the policy offered, not the extent it prefers",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 30, 30)),
                nestedRowChildBounds(),
                "and must have measured under it, since its weighted child takes the width it was granted",
            )
        }

    private companion object {
        /** The bounds the one row nested inside the container under test assigned its own children. */
        fun ComposeSwingTest.nestedRowChildBounds(): List<Rectangle> =
            (onNodeWithTag(CONTAINER_TAG).fetch<JComponent>().getComponent(0) as JComponent)
                .components
                .map { it.bounds }
    }
}

/**
 * A policy that stacks its children down the container at the width the offer allows, [gap]
 * apart, and asks for as much space as the stack occupies within that offer. It reads [gap] on every measure.
 */
internal fun stackedRows(gap: () -> Int = { 0 }): MeasurePolicy =
    MeasurePolicy { measurables, constraints ->
        var remainingHeight = constraints.maxHeight
        val currentGap = gap()
        val placeables =
            measurables.mapIndexed { index, measurable ->
                val placeable =
                    measurable.measure(
                        Constraints(maxWidth = constraints.maxWidth, maxHeight = remainingHeight),
                    )
                remainingHeight = (remainingHeight - placeable.height).coerceAtLeast(0)
                if (index < measurables.lastIndex) {
                    remainingHeight = (remainingHeight - currentGap.coerceAtLeast(0)).coerceAtLeast(0)
                }
                placeable
            }
        val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
        val height = constraints.constrainHeight(constraints.maxHeight - remainingHeight)
        layout(width, height) {
            var y = 0
            for (placeable in placeables) {
                placeable.place(0, y)
                y += placeable.height + currentGap
            }
        }
    }
