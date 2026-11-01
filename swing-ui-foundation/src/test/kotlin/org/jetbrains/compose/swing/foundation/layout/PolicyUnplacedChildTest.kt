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
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.ComposeTimeoutException
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
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
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.swing.modifier.appearance.background as swingBackground

/** How a child the policy of a [Layout] stops placing is drawn, focused, shown and sized, and again once placed. */
class PolicyUnplacedChildTest {
    /**
     * A child the policy stops placing is not drawn and is not the component under any of its points, as androidx draws
     * no node its parent leaves unplaced, whether or not it is decorated; placed again, it paints and is hit as before.
     */
    @Test
    fun aChildThePolicyStopsPlacingNoLongerPaintsOrIsHit() =
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
                "the component under each point",
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
                "a child placed again is the component under its point",
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
    @ExclusiveWindowSystem
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
            val focused =
                try {
                    waitUntil(timeout = 5.seconds) { focusManager.focusOwner === other }
                    true
                } catch (_: ComposeTimeoutException) {
                    // The deadline passed: the window system focuses none of this process's windows.
                    false
                }
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

            onNodeWithTag("child").assertProperty(false, "the child must stay hidden") { isVisible }
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
            assertSame(
                child,
                onNodeWithTag("child").fetch<Component>(),
                "the component is updated in place, not rebuilt",
            )

            onNodeWithTag("child").assertProperty(
                true,
                "placing the child again shows it over the declaration made while unplaced",
            ) { isVisible }
        }

    /**
     * A child shown by a declaration made while the policy leaves it unplaced paints nothing and is not the component
     * under its point until the policy places it again.
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
            val child = container.onChild().fetch()

            placed = false
            awaitIdle()
            visible = true
            awaitIdle()

            container.onChild().assertProperty(false, "the policy must hide again a child it leaves unplaced") {
                isVisible
            }
            assertImagesPixelPerfect(renderImage(CHILD_WIDTH, CHILD_HEIGHT) {}, container.captureToImage())
            assertSame(
                container.fetch<Component>(),
                SwingUtilities.getDeepestComponentAt(container.fetch(), CHILD_WIDTH / 2, CHILD_HEIGHT / 2),
                "where the unplaced child stands, the container is the component under the point",
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
                "the placed child is the component under its point",
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
            placed = false
            awaitIdle()

            onNodeWithTag("child").assertProperty(false, "an unplaced child must be hidden") { isVisible }
            onNodeWithTag("child").assertProperty(Dimension(0, 0), "an unplaced child must be left at a zero size") {
                size
            }

            placed = true
            awaitIdle()

            onNodeWithTag("child").assertProperty(
                true,
                "a child declaring no visibility must be shown when placed again",
            ) { isVisible }
            onNodeWithTag("child").assertProperty(Dimension(CHILD_WIDTH, CHILD_HEIGHT), "and take its placed size") {
                size
            }
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

            onNodeWithTag("child").assertProperty("declared", "the declared name must stand") { name }
            onNodeWithTag("child").assertProperty(false, "the declared enabled(false) must stand") { isEnabled }
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
            assertSame(
                child,
                onNodeWithTag("child").fetch<Component>(),
                "the component is updated in place, not rebuilt",
            )

            onNodeWithTag("child").assertProperty(
                Dimension(0, 0),
                "the content's paint outsets must not grow an unplaced child",
            ) { size }
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
}
