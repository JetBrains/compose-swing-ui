package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.visible
import org.jetbrains.compose.swing.test.ComposeTimeoutException
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/**
 * A container written as a [MeasurePolicy] stops placing a child: what the child paints, takes and shows while it
 * is unplaced and once it is placed again.
 */
@ExclusiveWindowSystem
class LayoutUnplacedChildTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun unplacedChildrenNeitherPaintNorTakeHits(showWhileUnplaced: Boolean) =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            var shown by mutableStateOf(!showWhileUnplaced)
            val columns = if (showWhileUnplaced) 1 else 2
            setContent {
                UnplacedChildrenLayout(columns, showWhileUnplaced, placed = { placed }, shown = { shown })
            }
            val container = onNodeWithTag(CONTAINER_TAG).assertIsOfType<JComponent>()
            val child = container.onChildAt(0).fetch()
            val image = container.captureToImage()
            placed = false
            awaitIdle()
            if (showWhileUnplaced) {
                shown = true
                awaitIdle()
            }
            container.onChildAt(0).assertProperty(false) { isVisible }
            assertImagesPixelPerfect(renderImage(columns * CHILD_WIDTH, CHILD_HEIGHT) {}, container.captureToImage())
            container.assertProperty(true) {
                (0 until columns).all {
                    SwingUtilities.getDeepestComponentAt(
                        this,
                        it * CHILD_WIDTH + CHILD_WIDTH / 2,
                        CHILD_HEIGHT / 2,
                    ) === this
                }
            }
            placed = true
            awaitIdle()

            val expected =
                if (showWhileUnplaced) {
                    renderImage(CHILD_WIDTH, CHILD_HEIGHT) {
                        it.color = Color.RED
                        it.fillRect(0, 0, CHILD_WIDTH, CHILD_HEIGHT)
                    }
                } else {
                    image
                }
            assertImagesPixelPerfect(expected, container.captureToImage())
            container.assertProperty(true) {
                val children = childrenInDeclarationOrder()
                children.size == columns &&
                    (0 until columns).all {
                        SwingUtilities.getDeepestComponentAt(
                            this,
                            it * CHILD_WIDTH + CHILD_WIDTH / 2,
                            CHILD_HEIGHT / 2,
                        ) === children[it]
                    }
            }
            if (showWhileUnplaced) {
                container.assertProperty(true) {
                    SwingUtilities.getDeepestComponentAt(this, CHILD_WIDTH / 2, CHILD_HEIGHT / 2) === child
                }
            }
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
            background.onNodeWithTag("first").assertProperty(true, "the background window's focus moves") {
                frame.mostRecentFocusOwner === this
            }
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
                            Box(modifier = SwingModifier.shadow(4, Color.BLACK).styleable { preferredSize(10, 10) })
                        },
                        measurePolicy = { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints())
                            layout(10, 10) { if (placed) placeable.place(0, 0) }
                        },
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                    )
                }
            }
            val container = onNodeWithTag(CONTAINER_TAG)
            container.assertProperty(true, "a placed child's shadow must grow the container") { width > 10 }

            placed = false
            awaitIdle()

            container.assertProperty(Dimension(10, 10), "the container must shrink back to its own bounds") { size }
        }

    @ParameterizedTest
    @CsvSource("unset, false, true", "false, false, false", "true, true, true")
    fun placementRestoresVisibility(
        initial: String,
        hideWhileUnplaced: Boolean,
        expectedVisible: Boolean,
    ) = runComposeSwingTest {
        var placed by mutableStateOf(true)
        var shown by mutableStateOf(initial != "false")
        setContent {
            Layout(
                content = {
                    SizedChild(
                        0,
                        SwingModifier.testTag("child").name("declared"),
                        style = {
                            enabled(false)
                            if (initial != "unset") visible(shown)
                        },
                    )
                },
                measurePolicy = { measurables, constraints ->
                    val placeable = measurables.single().measure(constraints)
                    layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                },
            )
        }
        val child = onNodeWithTag("child")
        placed = false
        awaitIdle()
        child.assertProperty(false) { isVisible }
        if (initial == "unset") child.assertProperty(Dimension(0, 0)) { size }
        if (hideWhileUnplaced) {
            shown = false
            awaitIdle()
        }
        placed = true
        awaitIdle()

        child.assertProperty(expectedVisible) { isVisible }
        if (initial == "unset") child.assertProperty(Dimension(CHILD_WIDTH, CHILD_HEIGHT)) { size }
        child.assertProperty("declared") { name }
        child.assertProperty(false) { isEnabled }
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
                            Box(modifier = SwingModifier.shadow(4, Color.BLACK).styleable { preferredSize(10, 10) })
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(CHILD_WIDTH, CHILD_HEIGHT) { if (placed) placeable.place(20, 20) }
                    },
                    modifier = ContainerModifier.styleable(containerStyle(CHILD_WIDTH, CHILD_HEIGHT)),
                )
            }

            placed = false
            awaitIdle()

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

            val moved = onNodeWithTag("child")
            moved.assertProperty(true, "the move must keep the component") { this === unplaced }
            moved.assertProperty(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                "the moved child must show in its new container",
            ) { size }
            moved.assertProperty(true, "the moved child must be visible in its new container") { isVisible }
        }
}

@Composable
private fun UnplacedChildrenLayout(
    columns: Int,
    showWhileUnplaced: Boolean,
    placed: () -> Boolean,
    shown: () -> Boolean,
) {
    Layout(
        content = {
            Panel(
                PanelLayout.Flow(),
                style = {
                    background(Color.RED)
                    opaque(true)
                    if (showWhileUnplaced) visible(shown())
                },
            ) {}
            if (!showWhileUnplaced) Box(modifier = SwingModifier.background(brush = { _, _ -> Color.BLUE }))
        },
        measurePolicy = { measurables, _ ->
            val placeables = measurables.map { it.measure(Constraints.fixed(CHILD_WIDTH, CHILD_HEIGHT)) }
            layout(columns * CHILD_WIDTH, CHILD_HEIGHT) {
                if (placed()) {
                    placeables.forEachIndexed { column, child -> child.place(column * CHILD_WIDTH, 0) }
                }
            }
        },
        modifier = ContainerModifier.styleable(containerStyle(columns * CHILD_WIDTH, CHILD_HEIGHT)),
    )
}
