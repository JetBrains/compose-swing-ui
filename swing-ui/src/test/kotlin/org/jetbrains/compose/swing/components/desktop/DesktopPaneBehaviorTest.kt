package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [DesktopPane] over a real
 * [SwingApplier][org.jetbrains.compose.swing.node.SwingApplier]. Each assertion reads the rendered
 * [JDesktopPane] and its [JInternalFrame] children: a declared frame is hosted with its title, bounds,
 * controls, and modifier; a frame that declares no controls leaves its widget at the bare
 * `JInternalFrame`'s own defaults; a frame is the only child the desktop takes and it takes as many of
 * them as the composition declares; frames are added and removed dynamically and the frame standing on the
 * desktop follows the declaration driving it; metadata updates on recomposition; and the close control
 * routes through `onClose` while leaving the frame in place until the composition drops it.
 */
class DesktopPaneBehaviorTest {
    /**
     * How many internal-frame listeners this frame carries beyond the ones the installed look and
     * feel puts on every frame it realizes - the ones the declaration installed.
     *
     * A look and feel listens to the frames it realizes for its own purposes, and how many listeners
     * that takes is its own affair: Metal's frames carry one from the moment they are constructed and
     * Aqua's carry none. What a declaration installed is therefore the difference between the frame's
     * listeners and a bare frame's, measured on a frame of the same look and feel.
     */
    private val JInternalFrame.declaredInternalFrameListenerCount: Int
        get() = internalFrameListeners.size - JInternalFrame().internalFrameListeners.size

    @Test
    fun eachDeclaredFrameIsHostedWithItsTitleBoundsAndControls() = runComposeSwingTest {
        setContent {
            DesktopPane {
                InternalFrame(
                    title = "Editor",
                    bounds = Rectangle(10, 20, 300, 200),
                    onClose = { },
                    controls = InternalFrameControls(closable = true, iconifiable = true),
                ) { Label(text = "editor-body") }
            }
        }

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeOfType<JInternalFrame>().assert(SwingMatcher.hasTitle("Editor"))
        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertEquals(Rectangle(10, 20, 300, 200), frame.bounds, "the frame should take its declared bounds")
        onNodeOfType<JInternalFrame>().assertIsVisible()
        assertTrue(frame.isClosable, "the frame should be closable as declared")
        assertFalse(frame.isResizable, "the frame should not be resizable as declared")
        assertFalse(frame.isMaximizable, "the frame should not be maximizable as declared")
        assertTrue(frame.isIconifiable, "the frame should be iconifiable as declared")
        assertEquals(
            JInternalFrame.DO_NOTHING_ON_CLOSE,
            frame.defaultCloseOperation,
            "the frame should use the do-nothing close op",
        )
        onNodeWithText("editor-body").assertExists()
    }

    @Test
    fun aFrameThatDeclaresNoControlsMatchesTheBareWidget() = runComposeSwingTest {
        setContent {
            DesktopPane {
                InternalFrame(title = "Editor", bounds = Rectangle(0, 0, 100, 100), onClose = { }) { }
            }
        }

        // A bare JInternalFrame is constructed hidden; the wrapper shows every frame it hosts.
        onNodeOfType<JInternalFrame>().assertTreeMatches(JInternalFrame("Editor").apply { isVisible = true })
    }

    @Test
    fun aChildThatIsNoFrameIsRefused() = runComposeSwingTest {
        // A desktop holds frames, each of them placed by the bounds it was declared with; a component
        // merely added to the desktop is placed by nothing at all and painted by nobody. It is refused
        // as it arrives instead, rather than left for the first frame declared after it to be blamed for.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    DesktopPane {
                        Label(text = "loose")
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            "holds each child in one of its own regions" in message,
            "the refusal should say the desktop places its children itself: $message",
        )
        assertTrue("JDesktopPane" in message, "the refusal should name the host: $message")
        assertTrue("JLabel" in message, "the refusal should name the child that named no place: $message")
        assertTrue(
            "InternalFrame" in message,
            "the refusal should name the call that would place the child: $message",
        )
    }

    @Test
    fun aDesktopHoldsEveryFrameDeclaredOnIt() = runComposeSwingTest {
        // The frames of a desktop share one region of it rather than each claiming one of its own, so
        // two frames declared alike are two frames rather than one taking the other's place.
        setContent {
            DesktopPane {
                InternalFrame(
                    title = "Notes",
                    bounds = Rectangle(0, 0, 120, 90),
                    onClose = { },
                ) { Label(text = "first") }
                InternalFrame(
                    title = "Notes",
                    bounds = Rectangle(20, 20, 120, 90),
                    onClose = { },
                ) { Label(text = "second") }
            }
        }

        onAllNodesOfType<JInternalFrame>().assertCountEquals(2)
        onNodeWithText("first").assertExists()
        onNodeWithText("second").assertExists()
    }

    @Test
    fun theFrameStandingOnTheDesktopFollowsTheDeclarationDrivingIt() = runComposeSwingTest {
        var editing by mutableStateOf(false)
        setContent {
            // Which frame the desktop carries is decided at composition time, so every pass hands the
            // desktop a different declaration: the frame that leaves is taken off the desktop and the
            // one that arrives stands in its place, both within the one pass.
            val editorShown = editing
            DesktopPane {
                if (editorShown) {
                    InternalFrame(title = "Editor", bounds = Rectangle(0, 0, 200, 150), onClose = { }) {
                        Label(text = "editor-body")
                    }
                } else {
                    InternalFrame(title = "Viewer", bounds = Rectangle(0, 0, 200, 150), onClose = { }) {
                        Label(text = "viewer-body")
                    }
                }
            }
        }

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeOfType<JInternalFrame>().assert(SwingMatcher.hasTitle("Viewer"))
        onNodeWithText("viewer-body").assertExists()

        editing = true
        awaitIdle()
        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeOfType<JInternalFrame>().assert(SwingMatcher.hasTitle("Editor"))
        onNodeWithText("editor-body").assertExists()
        onNodeWithText("viewer-body").assertDoesNotExist()

        editing = false
        awaitIdle()
        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeOfType<JInternalFrame>().assert(SwingMatcher.hasTitle("Viewer"))
        onNodeWithText("editor-body").assertDoesNotExist()
    }

    @Test
    fun framesAreAddedAndRemovedDynamically() = runComposeSwingTest {
        var showSecond by mutableStateOf(true)
        setContent {
            DesktopPane {
                InternalFrame(title = "One", bounds = Rectangle(0, 0, 100, 100), onClose = { }) { Label(text = "1") }
                if (showSecond) {
                    InternalFrame(
                        title = "Two",
                        bounds = Rectangle(0, 0, 100, 100),
                        onClose = { },
                    ) { Label(text = "2") }
                }
            }
        }

        onAllNodesOfType<JInternalFrame>().assertCountEquals(2)

        showSecond = false
        awaitIdle()
        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeOfType<JInternalFrame>().assert(SwingMatcher.hasTitle("One"))

        showSecond = true
        awaitIdle()
        onAllNodesOfType<JInternalFrame>().assertCountEquals(2)
    }

    @Test
    fun frameMetadataUpdatesOnRecomposition() = runComposeSwingTest {
        var title by mutableStateOf("Old")
        var resizable by mutableStateOf(true)
        var bounds by mutableStateOf(Rectangle(0, 0, 100, 100))
        setContent {
            DesktopPane {
                InternalFrame(
                    title = title,
                    bounds = bounds,
                    onClose = { },
                    controls = InternalFrameControls(resizable = resizable),
                ) { Label(text = "body") }
            }
        }

        // The very frame the first pass realized: a later pass has to update it rather than replace it.
        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertEquals("Old", frame.title, "the frame should start with its original title")
        assertTrue(frame.isResizable, "the frame should start resizable")

        title = "New"
        resizable = false
        bounds = Rectangle(40, 50, 250, 150)
        awaitIdle()

        assertEquals("New", frame.title, "title did not update on recomposition")
        assertFalse(frame.isResizable, "resizable did not update on recomposition")
        assertEquals(Rectangle(40, 50, 250, 150), frame.bounds, "bounds did not update on recomposition")

        title = "Old"
        resizable = true
        bounds = Rectangle(0, 0, 100, 100)
        awaitIdle()
        assertEquals("Old", frame.title, "the title follows the state back to its original value")
        assertTrue(frame.isResizable, "the resize control follows the state back to its original value")
        assertEquals(Rectangle(0, 0, 100, 100), frame.bounds, "the bounds follow the state back")
    }

    @Test
    fun activatingCloseRoutesThroughOnCloseWithoutClosingTheFrame() = runComposeSwingTest {
        var closes = 0
        var show by mutableStateOf(true)
        setContent {
            DesktopPane {
                if (show) {
                    InternalFrame(
                        title = "Closable",
                        bounds = Rectangle(0, 0, 100, 100),
                        onClose = { closes++ },
                    ) { Label(text = "body") }
                }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()

        // Headless: no realized peer, so dispatch the close action that a title-bar close button
        // would otherwise fire. The controlled frame stays in place; only onClose is invoked.
        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(1, closes, "onClose was not invoked when the close control fired")
        onNodeOfType<JInternalFrame>().assertExists()

        // The caller actually closes it by dropping it from the composition.
        show = false
        awaitIdle()
        onNodeOfType<JInternalFrame>().assertDoesNotExist()
    }

    @Test
    fun rawInternalFrameListenerOverloadReceivesFrameEvents() = runComposeSwingTest {
        var closings = 0
        val listener =
            object : InternalFrameAdapter() {
                override fun internalFrameClosing(event: InternalFrameEvent) {
                    closings++
                }
            }
        setContent {
            DesktopPane {
                InternalFrame(
                    title = "Listened",
                    bounds = Rectangle(0, 0, 100, 100),
                    internalFrameListener = listener,
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()

        // The raw listener is attached as-is; the close action fires its internalFrameClosing.
        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(1, closings, "the raw InternalFrameListener did not receive the close event")
        onNodeOfType<JInternalFrame>().assertExists()
    }

    @Test
    fun everyControlFollowsTheStateDrivingIt() = runComposeSwingTest {
        var controls by mutableStateOf(InternalFrameControls())
        setContent {
            DesktopPane {
                InternalFrame(
                    title = "Editor",
                    bounds = Rectangle(0, 0, 100, 100),
                    onClose = { },
                    controls = controls,
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertFalse(frame.isClosable, "a frame declares every control off until asked for one")
        assertFalse(frame.isResizable, "a frame declares every control off until asked for one")
        assertFalse(frame.isMaximizable, "a frame declares every control off until asked for one")
        assertFalse(frame.isIconifiable, "a frame declares every control off until asked for one")

        controls =
            InternalFrameControls(closable = true, resizable = true, maximizable = true, iconifiable = true)
        awaitIdle()
        assertTrue(frame.isClosable, "the close control follows the state driving it")
        assertTrue(frame.isResizable, "the resize control follows the state driving it")
        assertTrue(frame.isMaximizable, "the maximize control follows the state driving it")
        assertTrue(frame.isIconifiable, "the iconify control follows the state driving it")

        controls = InternalFrameControls()
        awaitIdle()
        assertFalse(frame.isClosable, "the close control follows the declared value back off")
        assertFalse(frame.isResizable, "the resize control follows the declared value back off")
        assertFalse(frame.isMaximizable, "the maximize control follows the declared value back off")
        assertFalse(frame.isIconifiable, "the iconify control follows the declared value back off")
    }

    @Test
    fun theFrameModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Edits the document")
        setContent {
            DesktopPane {
                InternalFrame(
                    title = "Editor",
                    bounds = Rectangle(0, 0, 100, 100),
                    onClose = { },
                    modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertEquals("Edits the document", frame.toolTipText, "the modifier applies to the frame")

        tip = "Holds the open document"
        awaitIdle()
        assertEquals("Holds the open document", frame.toolTipText, "the modifier follows the state driving it")

        tip = null
        awaitIdle()
        assertNull(frame.toolTipText, "dropping the element restores the tooltip the frame had without it")
    }

    @Test
    fun theFrameModifierFollowsTheStateOnTheRawListenerOverload() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Edits the document")
        var closings = 0
        val listener =
            object : InternalFrameAdapter() {
                override fun internalFrameClosing(event: InternalFrameEvent) {
                    closings++
                }
            }
        setContent {
            DesktopPane {
                InternalFrame(
                    title = "Listened",
                    bounds = Rectangle(0, 0, 100, 100),
                    internalFrameListener = listener,
                    modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertEquals("Edits the document", frame.toolTipText, "the modifier applies to the frame")

        tip = null
        awaitIdle()
        assertNull(frame.toolTipText, "dropping the element restores the tooltip the frame had without it")

        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(1, closings, "a modifier change must not displace the listener the overload installs")
        assertEquals(1, frame.declaredInternalFrameListenerCount, "the listener stays attached exactly once")
    }

    @Test
    fun theCloseControlRunsTheMostRecentlyComposedCallback() = runComposeSwingTest {
        var round by mutableStateOf(1)
        val runs = mutableListOf<Int>()
        setContent {
            // The round is captured at composition time, so every pass declares a callback that
            // reports a different value: a callback captured once when the frame was built keeps
            // reporting the round it was born in, however often the declaration changes.
            val current = round
            DesktopPane {
                InternalFrame(
                    title = "Closable",
                    bounds = Rectangle(0, 0, 100, 100),
                    onClose = { runs += current },
                    controls = InternalFrameControls(closable = true),
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(listOf(1), runs, "the close control runs the composed callback")

        round = 2
        awaitIdle()
        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(listOf(1, 2), runs, "the close control runs the recomposed callback, not the one it replaced")
        assertEquals(1, frame.declaredInternalFrameListenerCount, "recomposition must not stack up frame listeners")
    }

    @Test
    fun swappingTheRawListenerHandsFrameEventsToTheNewInstance() = runComposeSwingTest {
        var second by mutableStateOf(false)
        var first = 0
        var latest = 0
        val firstListener =
            object : InternalFrameAdapter() {
                override fun internalFrameClosing(event: InternalFrameEvent) {
                    first++
                }
            }
        val secondListener =
            object : InternalFrameAdapter() {
                override fun internalFrameClosing(event: InternalFrameEvent) {
                    latest++
                }
            }
        setContent {
            DesktopPane {
                InternalFrame(
                    title = "Listened",
                    bounds = Rectangle(0, 0, 100, 100),
                    internalFrameListener = if (second) secondListener else firstListener,
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(1, first, "the declared listener receives the frame event")

        second = true
        awaitIdle()
        assertEquals(1, frame.declaredInternalFrameListenerCount, "the replaced listener must be detached, not stacked")

        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(1, latest, "the newly declared listener receives the frame event")
        assertEquals(1, first, "the listener that left the declaration no longer fires")
    }

    @Test
    fun theFrameBodyFollowsTheDeclarationDrivingIt() = runComposeSwingTest {
        var second by mutableStateOf(false)
        setContent {
            // How many children the body declares is decided at composition time, so every pass hands
            // the frame a different declaration: a frame that keeps the body it was first given would
            // go on showing the children it started with.
            val bothChildren = second
            DesktopPane {
                InternalFrame(title = "Editor", bounds = Rectangle(0, 0, 200, 200), onClose = { }) {
                    Label(text = "first")
                    if (bothChildren) Label(text = "second")
                }
            }
        }

        // A frame's body children live in its content pane, which is what `JInternalFrame.add`
        // targets; the frame itself keeps its root pane as its only direct child throughout.
        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertEquals(1, frame.contentPane.componentCount, "the body holds the single declared child")
        onNodeWithText("second").assertDoesNotExist()

        second = true
        awaitIdle()
        assertEquals(2, frame.contentPane.componentCount, "the added declaration reaches the body")
        onNodeWithText("second").assertExists()

        second = false
        awaitIdle()
        assertEquals(1, frame.contentPane.componentCount, "the dropped declaration leaves the body")
        onNodeWithText("second").assertDoesNotExist()
        onNodeWithText("first").assertExists()
        assertEquals(frame.rootPane, frame.getComponent(0), "the frame keeps its own root pane")
    }

    @Test
    fun droppingTheOnlyBodyChildLeavesTheFrameIntact() = runComposeSwingTest {
        var show by mutableStateOf(true)
        setContent {
            // Whether the body declares a child is decided at composition time, so the frame is handed
            // a different declaration each pass rather than one lambda that re-reads the state.
            val hasChild = show
            DesktopPane {
                InternalFrame(title = "Editor", bounds = Rectangle(0, 0, 200, 200), onClose = { }) {
                    if (hasChild) Label(text = "body")
                }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        onNodeWithText("body").assertExists()

        show = false
        awaitIdle()
        onNodeWithText("body").assertDoesNotExist()
        assertEquals(0, frame.contentPane.componentCount, "the dropped declaration leaves the body")
        assertEquals(frame.rootPane, frame.getComponent(0), "the frame keeps its own root pane")

        show = true
        awaitIdle()
        onNodeWithText("body").assertExists()
        assertEquals(1, frame.contentPane.componentCount, "the re-added declaration returns to the body")
    }

    @Test
    fun theDesktopPanesOwnModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Workspace")
        setContent {
            DesktopPane(modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier) {
                InternalFrame(
                    title = "Frame",
                    bounds = Rectangle(0, 0, 100, 100),
                    onClose = { },
                ) { Label(text = "body") }
            }
        }

        val pane = onNodeOfType<JDesktopPane>().fetch()
        assertEquals("Workspace", pane.toolTipText, "the modifier applies to the desktop pane itself")

        tip = "Open documents"
        awaitIdle()
        assertEquals("Open documents", pane.toolTipText, "the modifier follows the state driving it")

        tip = null
        awaitIdle()
        assertNull(pane.toolTipText, "dropping the element restores the tooltip the pane had without it")
    }

    @Test
    fun disposingTheDesktopPaneTearsItDown() = runComposeSwingTest {
        var show by mutableStateOf(true)
        setContent {
            if (show) {
                DesktopPane {
                    InternalFrame(
                        title = "Frame",
                        bounds = Rectangle(0, 0, 100, 100),
                        onClose = { },
                    ) { Label(text = "body") }
                }
            }
        }

        onNodeOfType<JDesktopPane>().assertExists()
        onNodeWithText("body").assertExists()

        show = false
        awaitIdle()

        onNodeOfType<JDesktopPane>().assertDoesNotExist()
        onNodeWithText("body").assertDoesNotExist()
    }
}
