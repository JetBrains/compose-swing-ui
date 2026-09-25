package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.click
import org.jetbrains.compose.swing.components.GlassPaneHostScope
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.OwnedGlassPaneContractTest
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasText
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isOfType
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for an [InternalFrameScope.GlassPane] beyond the shared contract: the frame's other
 * content stays in its content pane, and clicks and cursors below the title bar.
 */
class InternalFrameGlassPaneTest : OwnedGlassPaneContractTest() {
    @Composable
    override fun Host(content: @Composable GlassPaneHostScope.() -> Unit) =
        Frame { FrameGlassPaneHostScope(this).content() }

    override fun ComposeSwingTest.glassPanes(): List<Component> =
        onAllNodesOfType<JInternalFrame>().fetchAll().map { it.glassPane }

    @Test
    fun aLayoutPassedFirstLaysThePaneOut() = runComposeSwingTest {
        setContent {
            Frame {
                GlassPane(PanelLayout.Border()) { Label(text = "veil", modifier = SwingModifier.north()) }
            }
        }

        val pane = assertIs<Container>(onNodeOfType<JInternalFrame>().fetch().glassPane)
        val layout = assertIs<BorderLayout>(pane.layout, "the declared layout is installed on the pane")
        assertSame(
            pane.components.single(),
            layout.getLayoutComponent(BorderLayout.NORTH),
            "a child's placement call reaches the pane's layout",
        )
    }

    @Test
    fun theFramesContentStaysInItsContentPaneBesideAGlassPane() = runComposeSwingTest {
        setContent {
            Frame {
                Label(text = "before")
                GlassPane { Label(text = "hint") }
                Label(text = "after")
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertEquals(
            listOf("before", "after"),
            frame.contentPane.components.map { (it as JLabel).text },
            "the frame's other content lands in its content pane, in declaration order",
        )
        assertEquals(
            listOf("hint"),
            (frame.glassPane as Container).components.map { (it as JLabel).text },
            "and the glass pane holds only its own content",
        )
    }

    @Test
    fun aClickReachesTheFramesContentThroughAPaneUnlessItsModifierListens() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var listening by mutableStateOf(false)
        var contentClicks = 0
        var paneClicks = 0
        setContent {
            // The window is realized but never shown: its peer is what dispatches a click to the component
            // under the pointer. It is realized at the size it packs to, which the desktop's preferred size
            // decides, so no later resize reported by the window system lands under the test.
            Window(onCloseRequest = {}, title = WINDOW_TITLE, visible = false) {
                DesktopPane(modifier = SwingModifier.preferredSize(Dimension(320, 240))) {
                    InternalFrame(title = "frame", bounds = Rectangle(0, 0, 200, 120), onClose = {}) {
                        Button(text = "content", onClick = { contentClicks++ })
                        GlassPane(
                            modifier =
                                if (listening) {
                                    SwingModifier.mouseListener(onMouseClicked = { paneClicks++ })
                                } else {
                                    SwingModifier
                                },
                        ) {}
                    }
                }
            }
        }

        val window = onWindowWithTitle(WINDOW_TITLE)
        val frame = window.fetch<JFrame>()
        val button = window.onNode(isOfType<JButton>() and hasText("content")).fetch<JButton>()
        val overTheContent = SwingUtilities.convertPoint(button, button.width / 2, button.height / 2, frame)

        frame.click(overTheContent)
        assertEquals(1, contentClicks, "a pane that declares no listener should let the click reach the content")

        listening = true
        awaitIdle()
        frame.click(overTheContent)
        assertEquals(1, paneClicks, "a pane whose modifier declares a mouse listener should take the click")
        assertEquals(1, contentClicks, "and keep it from the frame's content")

        listening = false
        awaitIdle()
        frame.click(overTheContent)
        assertEquals(2, contentClicks, "a listener the modifier no longer declares should let clicks through again")
        assertEquals(1, paneClicks, "and receive no more of them")
    }

    @Test
    fun aCursorDeclaredOnThePaneIsTheOneShownBelowTheTitleBar() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            // The window is realized but never shown, at the size the desktop's preferred size packs it to.
            Window(onCloseRequest = {}, title = WINDOW_TITLE, visible = false) {
                DesktopPane(modifier = SwingModifier.preferredSize(Dimension(320, 240))) {
                    InternalFrame(title = "frame", bounds = Rectangle(0, 0, 200, 120), onClose = {}) {
                        Button(text = "content", onClick = {})
                        GlassPane(modifier = SwingModifier.cursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))) {}
                    }
                }
            }
        }

        val window = onWindowWithTitle(WINDOW_TITLE)
        val frame = window.fetch<JFrame>()
        val button = window.onNode(isOfType<JButton>() and hasText("content")).fetch<JButton>()
        val internalFrame = window.onNode(isOfType<JInternalFrame>()).fetch<JInternalFrame>()
        val overTheContent = SwingUtilities.convertPoint(button, button.width / 2, button.height / 2, frame)
        // The frame's layout puts its title bar, painted by a title pane or by the frame's border depending on
        // the look and feel, above its root pane.
        val rootPane = internalFrame.rootPane
        val overTheTitleBar = SwingUtilities.convertPoint(internalFrame, internalFrame.width / 2, rootPane.y / 2, frame)

        val belowTheTitleBar = SwingUtilities.getDeepestComponentAt(frame, overTheContent.x, overTheContent.y)
        assertSame(internalFrame.glassPane, belowTheTitleBar, "the pane covers the frame's content")
        assertEquals(Cursor.WAIT_CURSOR, belowTheTitleBar.cursor.type, "and the pane's cursor is the one shown there")

        val inTheTitleBar = SwingUtilities.getDeepestComponentAt(frame, overTheTitleBar.x, overTheTitleBar.y)
        assertFalse(SwingUtilities.isDescendingFrom(inTheTitleBar, rootPane), "the title bar is above the pane")
        assertEquals(Cursor.DEFAULT_CURSOR, inTheTitleBar.cursor.type, "and keeps the cursor it inherits")
    }

    @Test
    fun aGlassPaneThatIsNotAContainerIsRefusedNamingWhatTheFrameHolds() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        setContent {
            Frame {
                if (showPane) GlassPane { Label(text = "hint") }
            }
        }
        val message =
            failureOf {
                onNodeOfType<JInternalFrame>().fetch().glassPane = object : Component() {}
                showPane = true
                awaitIdle()
            }
        assertTrue(
            "ExistingSwingNode claims a Container, but its claim returned a" in message,
            "a glass pane that cannot hold content should be refused by the claim's type check: $message",
        )
    }

    @Test
    fun aGlassPaneComposedInsideTheFramesContentIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Frame {
                        // LayoutScopeMarker keeps the frame's receiver out of reach inside Panel's content.
                        val frame = this
                        Panel { frame.GlassPane { Label(text = "hint") } }
                    }
                }
            }

        assertTrue(
            "Compose it directly under the JInternalFrame" in message,
            "a glass pane composed below the frame's content should be refused by the parent check: $message",
        )
    }

    @Test
    fun twoGlassPanesInOneFrameAreRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Frame {
                        GlassPane { Label(text = "first") }
                        GlassPane { Label(text = "second") }
                    }
                }
            }

        assertTrue(
            "GlassPane { } is declared twice at once in one JInternalFrame, and both declarations would configure " +
                "its JPanel. Declare one, and put the choice inside it." in message,
            "a second glass pane in one frame should be refused naming the GlassPane declaration: $message",
        )
    }

    @Composable
    private inline fun Frame(crossinline content: @Composable InternalFrameScope.() -> Unit) {
        DesktopPane {
            InternalFrame(title = "frame", bounds = Rectangle(0, 0, 200, 120), onClose = {}) { content() }
        }
    }

    private companion object {
        const val WINDOW_TITLE = "internal frame glass pane clicks"
    }
}

private class FrameGlassPaneHostScope(
    private val frame: InternalFrameScope,
) : GlassPaneHostScope {
    @Composable
    override fun GlassPane(
        modifier: SwingModifier,
        content: @Composable PanelScope.() -> Unit,
    ) = frame.GlassPane(modifier, content)

    @Composable
    override fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier,
        content: @Composable S.() -> Unit,
    ) = frame.GlassPane(layout, modifier, content)
}
