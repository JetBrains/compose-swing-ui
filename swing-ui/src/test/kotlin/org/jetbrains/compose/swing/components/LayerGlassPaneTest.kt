package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.click
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isOfType
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JLayer
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for a [LayerScope.GlassPane] beyond the shared contract: which points the pane answers
 * for, and which component a click reaches.
 *
 * The pane is laid out over the whole layer, so what it answers for a point decides what everything that
 * finds a component by geometry reaches - the cursor shown, the drop target found. The tests lay the
 * layer's tree out by hand at the size they set, which gives the pane and its children the bounds they
 * are hit-tested against.
 *
 * Which component a click reaches is decided by the window the layer stands in, so the case about clicks
 * composes under a realized `Window` and is skipped in headless environments.
 */
class LayerGlassPaneTest : OwnedGlassPaneContractTest() {
    @Composable
    override fun Host(content: @Composable GlassPaneHostScope.() -> Unit) {
        Layer {
            Label(text = "body")
            LayerGlassPaneHostScope(this).content()
        }
    }

    override fun ComposeSwingTest.glassPanes(): List<Component> =
        onAllNodesOfType<JLayer<*>>().fetchAll().map { it.glassPane }

    @Test
    fun theGlassPaneAnswersForAPointOnlyWhereOneOfItsShownChildrenLies() = runComposeSwingTest {
        setContent {
            Layer(modifier = SwingModifier.preferredSize(SIZE), onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body")
                GlassPane(PanelLayout.Border()) {
                    // Covers the top of the pane alone, leaving points below it to the view.
                    Label(text = "hint", modifier = SwingModifier.north().preferredSize(Dimension(60, 20)))
                }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val view = assertNotNull(layer.view, "the declared child should fill the view region")
        val pane = assertNotNull(layer.glassPane, "a layer builds a glass pane of its own")
        val hint = pane.components.single()
        val x = SIZE.width / 2
        val overTheChild = hint.height / 2
        val besideTheChild = (hint.height + SIZE.height) / 2

        assertSame(
            view,
            SwingUtilities.getDeepestComponentAt(layer, x, besideTheChild),
            "a query by geometry must reach the view where the pane holds no child, where a pane " +
                "answering for every point in itself would stop it",
        )
        assertSame(
            hint,
            SwingUtilities.getDeepestComponentAt(layer, x, overTheChild),
            "and stop at the pane's own child where one lies",
        )

        assertTrue(pane.contains(x, overTheChild), "the pane answers for a point one of its children covers")
        assertFalse(pane.contains(x, besideTheChild), "and for no point beside them")

        hint.isVisible = false
        assertFalse(pane.contains(x, overTheChild), "a child that is not shown covers no point")
    }

    @Test
    fun theGlassPaneAnswersForAPointItHasItsOwnReasonToTake() = runComposeSwingTest {
        setContent {
            Layer(modifier = SwingModifier.preferredSize(SIZE), onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body")
                GlassPane {}
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val pane = assertNotNull(layer.glassPane, "a layer builds a glass pane of its own")
        val listener = object : MouseAdapter() {}
        val x = SIZE.width / 2
        val y = SIZE.height / 2

        assertFalse(pane.contains(x, y), "a pane holding nothing and listening for nothing answers for no point")

        // Each reason is taken away again, so that the next one is what the answer after it turns on.
        pane.addMouseListener(listener)
        assertTrue(pane.contains(x, y), "a pane carrying a mouse listener takes the point")
        pane.removeMouseListener(listener)
        assertFalse(pane.contains(x, y), "and lets it through once that listener is gone")

        pane.addMouseMotionListener(listener)
        assertTrue(pane.contains(x, y), "a mouse motion listener is a reason of its own")
        pane.removeMouseMotionListener(listener)
        assertFalse(pane.contains(x, y), "and lets the point through once it is gone")

        pane.addMouseWheelListener(listener)
        assertTrue(pane.contains(x, y), "so is a mouse wheel listener")
        pane.removeMouseWheelListener(listener)
        assertFalse(pane.contains(x, y), "and lets the point through once it is gone")

        pane.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        assertTrue(pane.contains(x, y), "so is a cursor of its own, which only the pane can show")
        assertFalse(pane.contains(x, SIZE.height + 1), "though never for a point outside its own bounds")
    }

    @Test
    fun aClickReachesTheViewThroughAPaneWithACursorAndNotThroughOneThatListens() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var listening by mutableStateOf(false)
        var viewClicks = 0
        var paneClicks = 0
        setContent {
            // The window is realized but never shown: its peer is what dispatches a click to the component
            // under the pointer. It is realized at the size it packs to, which the layer's preferred size
            // decides: the window system reports each resize of a realized window back later, so a resize
            // after realizing could have the packed size land after it and shrink the window under the test.
            Window(onCloseRequest = {}, title = WINDOW_TITLE, visible = false) {
                Layer(
                    modifier = SwingModifier.preferredSize(Dimension(320, 240)),
                    onPaint = { _, _, _, paintView -> paintView() },
                ) {
                    Button(text = "view", onClick = { viewClicks++ })
                    GlassPane(
                        modifier =
                            SwingModifier.cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)).let {
                                if (listening) it.mouseListener(onMouseClicked = { paneClicks++ }) else it
                            },
                    ) {}
                }
            }
        }

        val window = onWindowWithTitle(WINDOW_TITLE)
        val frame = window.fetch<JFrame>()
        val layer = window.onNode(isOfType<JLayer<*>>()).fetch<JLayer<*>>()
        val pane = assertNotNull(layer.glassPane, "a layer builds a glass pane of its own")
        val overTheView = SwingUtilities.convertPoint(layer, layer.width / 2, layer.height / 2, frame)

        assertSame(
            pane,
            SwingUtilities.getDeepestComponentAt(frame, overTheView.x, overTheView.y),
            "a pane with a cursor of its own is what a query by geometry finds, so the cursor shown is its own",
        )
        frame.click(overTheView)
        assertEquals(1, viewClicks, "a pane with only a cursor of its own should let the click reach the view")

        listening = true
        awaitIdle()
        frame.click(overTheView)
        assertEquals(1, paneClicks, "a pane whose modifier declares a mouse listener should take the click")
        assertEquals(1, viewClicks, "and keep it from the view")

        listening = false
        awaitIdle()
        frame.click(overTheView)
        assertEquals(2, viewClicks, "a listener the modifier no longer declares should let clicks through again")
    }

    @Test
    fun aBorderLayoutLetsASingleChildFillTheLayer() = runComposeSwingTest {
        setContent {
            Layer(modifier = SwingModifier.preferredSize(SIZE)) {
                Label(text = "body")
                GlassPane(PanelLayout.Border()) { Label(text = "veil") }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val pane = assertNotNull(layer.glassPane, "a layer builds a glass pane of its own")

        assertIs<BorderLayout>(pane.layout, "the declared layout is installed on the pane")
        assertEquals(Rectangle(SIZE), pane.bounds, "the pane covers the layer")
        assertEquals(Rectangle(SIZE), pane.components.single().bounds, "and its one child fills it")
    }

    @Test
    fun aGlassPaneComposedInsideTheLayersViewIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Layer {
                        // LayoutScopeMarker keeps the layer's receiver out of reach inside Panel's content.
                        val layer = this
                        Panel { layer.GlassPane { Label(text = "hint") } }
                    }
                }
            }

        assertTrue(
            "Compose it directly under the JLayer" in message,
            "a glass pane composed below the layer's content should be refused by the parent check: $message",
        )
    }

    @Test
    fun twoGlassPanesInOneLayerAreRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Layer {
                        Label(text = "body")
                        GlassPane { Label(text = "first") }
                        GlassPane { Label(text = "second") }
                    }
                }
            }

        assertTrue(
            "GlassPane { } is declared twice at once in one JLayer" in message,
            "a second glass pane in one layer should be refused as a second declaration of its pane: $message",
        )
    }

    @Test
    fun aLayerDeclaringNoCallbackKeepsShowingItsViewAfterItsOverlayLeaves() = runComposeSwingTest {
        var loading by mutableStateOf(true)
        var background by mutableStateOf(Color.RED)
        setContent {
            Layer(modifier = SwingModifier.background(background)) {
                Label(text = "body")
                if (loading) GlassPane { Label(text = "loading") }
            }
        }
        awaitIdle()
        val layer = onNodeOfType<JLayer<*>>().fetch()
        val pane = assertNotNull(layer.glassPane, "a layer builds a glass pane of its own")
        assertTrue(pane.isVisible, "the overlay is shown while it is declared")

        loading = false
        awaitIdle()
        background = Color.BLUE
        awaitIdle()

        assertFalse(pane.isVisible, "an overlay that comes and goes leaves the layer showing its view alone")
        assertEquals("body", (layer.view as JLabel).text, "and the layer keeps its view")
        assertEquals(Color.BLUE, layer.background, "and goes on applying its own declaration")
    }

    private companion object {
        val SIZE = Dimension(120, 80)

        const val WINDOW_TITLE = "layer glass pane clicks"
    }
}

private class LayerGlassPaneHostScope(
    private val layer: LayerScope,
) : GlassPaneHostScope {
    @Composable
    override fun GlassPane(
        modifier: SwingModifier,
        content: @Composable PanelScope.() -> Unit,
    ) = layer.GlassPane(modifier, content)

    @Composable
    override fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier,
        content: @Composable S.() -> Unit,
    ) = layer.GlassPane(layout, modifier, content)
}
