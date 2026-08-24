package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.assumeFrameDeiconifies
import org.jetbrains.compose.swing.assumeFrameIconifies
import org.jetbrains.compose.swing.assumeKeyboardFocusIsPossible
import org.jetbrains.compose.swing.assumeWindowBecomesFocused
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.GlassPane
import org.jetbrains.compose.swing.window.LocalWindow
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.awaitApplication
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.RootPaneContainer
import javax.swing.WindowConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Behavioral coverage for the [LifecycleOwner] mounted content reads as its [LocalLifecycleOwner]:
 * which owner it is given, what that owner reports about where its content stands in the Swing tree, how
 * it follows that content, and how far one owner reaches.
 *
 * Every case reads the owner and its state off the content itself, so what is asserted is what an
 * application reading `LocalLifecycleOwner.current` sees. Where a case asserts one instance against
 * another, the claim is about which owner answers rather than about the state it holds.
 *
 * Which states are observable depends on what the host grants. A case that realizes a peer needs a
 * display; a case that turns on the keyboard focus needs a window system that makes this process's
 * windows the focused one, which a host running the tests as a background agent never does. Both are
 * declared as assumptions, so a host that withholds either reports the case as SKIPPED instead of
 * failing it. What a granted capability leads to is asserted rather than assumed: a focused window
 * whose content reports the wrong state still fails.
 *
 * A case that waits for a state waits for the one it expects and then asserts, so content that settles
 * on another state is reported as the state it holds rather than as a wait that ran out.
 *
 * The test harness mounts its root into a container that is deliberately never attached to a window, so
 * a composition mounted through `runComposeSwingTest` reports CREATED however long it is driven. A case
 * that observes STARTED or RESUMED therefore mounts into a real window of its own and shows it.
 */
@ExclusiveWindowSystem
class CompositionLifecycleTest {
    @Test
    fun contentThatHangsOffNoWindowReportsCreated() = runSwingTest {
        // A container of its own, in no window at all - the state a composition mounted into a container
        // built before it is shown opens in. It composes under a runtime of its own, so nothing above
        // states an owner and the root mints one following where its own container hangs.
        val runtime = SwingRecomposer.create(JPanel())
        try {
            val reader = LifecycleReader()
            val panel = JPanel()
            val handle = panel.setContent(parent = runtime.compositionContext) { reader.Observe() }

            assertEquals(
                Lifecycle.State.CREATED,
                reader.state,
                "content composed into a container that hangs off no window must report CREATED",
            )
            handle.dispose()
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun contentInAWindowThatDoesNotHoldTheFocusReportsStarted() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val panel = JPanel()
        val reader = LifecycleReader()
        val handle = panel.setContent { reader.Observe() }
        val frame = frameHolding(UNFOCUSED_TITLE, panel)
        try {
            awaitState(reader, Lifecycle.State.CREATED)
            assertEquals(
                Lifecycle.State.CREATED,
                reader.state,
                "content in a window Swing has given no peer yet must still report CREATED",
            )

            // Packing realizes the peer without showing the frame, so the window is never a candidate
            // for the focus and the state reached here is the shown-but-unfocused one on every host.
            frame.pack()

            awaitState(reader, Lifecycle.State.STARTED)
            assertEquals(
                Lifecycle.State.STARTED,
                reader.state,
                "content in a realized window that does not hold the focus must report STARTED",
            )
        } finally {
            handle.dispose()
            frame.dispose()
        }
    }

    @Test
    fun contentInAFocusedWindowReportsResumed() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            val panel = JPanel()
            val reader = LifecycleReader()
            val handle = panel.setContent { reader.Observe() }
            val frame = frameHolding(FOCUSED_TITLE, panel)
            try {
                frame.showAskingForFocus()
                assumeWindowBecomesFocused(frame)

                awaitState(reader, Lifecycle.State.RESUMED)
                assertEquals(
                    Lifecycle.State.RESUMED,
                    reader.state,
                    "content shown in the window that holds the keyboard focus must report RESUMED",
                )
            } finally {
                handle.dispose()
                frame.dispose()
            }
        }
    }

    @Test
    fun losingTheWindowFocusDropsToStartedAndTakingItBackResumes() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            val panel = JPanel()
            val reader = LifecycleReader()
            val handle = panel.setContent { reader.Observe() }
            val frame = frameHolding(FOCUS_MOVE_TITLE, panel)
            // A second window is what takes the focus away: the content stays exactly where it is in
            // the Swing tree, and only the focus of the window it is in moves.
            val other = frameHolding(FOCUS_STEALER_TITLE)
            try {
                frame.showAskingForFocus()
                assumeWindowBecomesFocused(frame)
                awaitState(reader, Lifecycle.State.RESUMED)
                assertEquals(
                    Lifecycle.State.RESUMED,
                    reader.state,
                    "content in the focused window must report RESUMED before the focus moves away",
                )

                other.showAskingForFocus()
                assumeWindowBecomesFocused(other)
                awaitState(reader, Lifecycle.State.STARTED)
                assertEquals(
                    Lifecycle.State.STARTED,
                    reader.state,
                    "content whose window has lost the focus must drop to STARTED while it stays shown",
                )

                frame.showAskingForFocus()
                assumeWindowBecomesFocused(frame)
                awaitState(reader, Lifecycle.State.RESUMED)
                assertEquals(
                    Lifecycle.State.RESUMED,
                    reader.state,
                    "content whose window takes the focus back must return to RESUMED",
                )
            } finally {
                handle.dispose()
                other.dispose()
                frame.dispose()
            }
        }
    }

    @Test
    fun minimizingTheWindowDropsToCreatedAndRestoringItShowsTheContentAgain() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val panel = JPanel()
        val reader = LifecycleReader()
        val handle = panel.setContent { reader.Observe() }
        val frame = frameHolding(MINIMIZED_TITLE, panel)
        try {
            // Iconification is a change the window system posts only for a window it is actually
            // showing, so the frame is shown here rather than merely realized as the other STARTED
            // cases are - a background agent shows a window without ever focusing it, which is the
            // unfocused STARTED this case starts from.
            frame.isVisible = true
            awaitState(reader, Lifecycle.State.STARTED)
            assertEquals(
                Lifecycle.State.STARTED,
                reader.state,
                "content in a shown window must report STARTED before it is minimized",
            )

            assumeFrameIconifies(frame)
            awaitState(reader, Lifecycle.State.CREATED)
            assertEquals(
                Lifecycle.State.CREATED,
                reader.state,
                "content whose window is minimized must report CREATED",
            )

            assumeFrameDeiconifies(frame)
            awaitStateAtLeast(reader, Lifecycle.State.STARTED)
            assertTrue(
                reader.state?.isAtLeast(Lifecycle.State.STARTED) == true,
                "content whose window is restored from minimization must be shown again, but its owner " +
                    "reports ${reader.state}",
            )
        } finally {
            handle.dispose()
            frame.dispose()
        }
    }

    @Test
    fun disposingContentGivenAnOwnerOfItsOwnEndsThatOwnerAtDestroyed() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // Nothing around this container answers with an owner: the composition it joins is the shared
        // one of a plain Swing frame, which states no locals, and no root above it in the Swing tree
        // has published one. The content is given an owner of its own, and the handle that mounted it
        // is what ends that owner.
        val panel = JPanel()
        val reader = LifecycleReader()
        val handle = panel.setContent { reader.Observe() }
        val frame = frameHolding(OWN_OWNER_DISPOSED_TITLE, panel)
        try {
            frame.pack()
            awaitState(reader, Lifecycle.State.STARTED)
            assertEquals(
                Lifecycle.State.STARTED,
                reader.state,
                "content in a realized window must report STARTED before it is disposed",
            )

            handle.dispose()

            assertEquals(
                Lifecycle.State.DESTROYED,
                reader.state,
                "disposing the handle a mount returned must end the owner that mount minted at DESTROYED",
            )
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun disposingAnIslandThatReadAnOwnerFromTheTreeLeavesThatOwnerLive() = runComposeSwingTest {
        lateinit var enclosing: CompositionContext
        setContent { enclosing = rememberCompositionContext() }
        awaitIdle()

        // The host joins the enclosing composition and reads the owner that composition carries. The
        // island hangs under the host and reads the same one rather than being given one of its own - and
        // an owner belongs to whoever minted it, not to every root that reads it.
        val hostContent = LifecycleReader()
        val host = JPanel()
        val hostHandle = host.setContent(parent = enclosing) { hostContent.Observe() }
        val hostOwner = assertNotNull(hostContent.owner, "the host content must have read an owner")

        val islandContent = LifecycleReader()
        val island = JPanel().also { host.add(it) }
        val islandHandle = island.setContent(parent = enclosing) { islandContent.Observe() }
        try {
            assertSame(
                hostOwner,
                islandContent.owner,
                "an island hanging under composed content must read the owner that content was given",
            )

            islandHandle.dispose()

            assertNotEquals(
                Lifecycle.State.DESTROYED,
                hostOwner.lifecycle.currentState,
                "disposing an island must not end an owner the island only read",
            )
            assertSame(
                hostOwner,
                hostContent.owner,
                "an owner an island only read must go on answering for the content that inherited it",
            )
        } finally {
            hostHandle.dispose()
        }
    }

    @Test
    fun aDestroyedOwnerStaysDestroyedWhenItsContentIsMovedAgain() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val panel = JPanel()
        val reader = LifecycleReader()
        val handle = panel.setContent { reader.Observe() }
        val frame = frameHolding(DISPOSED_TITLE, panel)
        val other = frameHolding(REPARENTED_TITLE)
        try {
            frame.pack()
            awaitState(reader, Lifecycle.State.STARTED)
            assertEquals(
                Lifecycle.State.STARTED,
                reader.state,
                "content in a realized window must report STARTED before it is disposed",
            )

            // Moving live content into another window disposes the composition that was following the
            // first one and composes it again under the second, so the owner that follows the content
            // from here on is a new instance. The old one is disposed synchronously inside this very
            // hierarchy dispatch, and the same event goes on to reach its own listener regardless - so
            // the window it hands the content off to must end up with exactly the new owner's
            // registration, however many times the content passes through it. A window focus listener
            // is installed by an owner and by nothing else here, so counting those counts the owners
            // the window is left holding.
            other.contentPane.add(panel)
            awaitEventsDelivered()
            assertEquals(
                1,
                other.windowFocusListeners.size,
                "moving live content into another window must leave only the new owner listening to it",
            )

            frame.contentPane.add(panel)
            awaitEventsDelivered()
            other.contentPane.add(panel)
            awaitEventsDelivered()
            assertEquals(
                1,
                other.windowFocusListeners.size,
                "a second move into the same window must not leave another owner listening beside the live one",
            )

            frame.contentPane.add(panel)
            awaitEventsDelivered()

            handle.dispose()
            assertEquals(
                Lifecycle.State.DESTROYED,
                reader.state,
                "disposing the mount must end the owner at DESTROYED",
            )

            // Taking the container back out of the window is a move a live owner would follow all the
            // way back to CREATED, and one a destroyed owner must not hear about at all: the hierarchy
            // notification is delivered on this very call, so an owner that still moved would fail here.
            frame.contentPane.remove(panel)
            awaitEventsDelivered()

            assertEquals(
                Lifecycle.State.DESTROYED,
                reader.state,
                "DESTROYED is terminal: a move made after disposal must leave the owner where it is",
            )
        } finally {
            frame.dispose()
            other.dispose()
        }
    }

    @Test
    fun aWindowStatesAnOwnerOfItsOwnRatherThanTheOneAroundIt() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val rootContent = LifecycleReader()
        val windowContent = LifecycleReader()
        // The peer is composed invisible: which owner the content reads is settled by the composition
        // it belongs to rather than by anything being shown.
        setContent {
            rootContent.Observe()
            Window(onCloseRequest = {}, title = OWN_OWNER_TITLE, visible = false) {
                windowContent.Observe()
            }
        }
        awaitIdle()

        val rootOwner = assertNotNull(rootContent.owner, "the root content must have read an owner")
        val windowOwner = assertNotNull(windowContent.owner, "the window content must have read an owner")
        assertNotSame(
            rootOwner,
            windowOwner,
            "attachment, minimization and focus are facts about one window, so a window's content must " +
                "read an owner of its own rather than the one around the declaration",
        )
    }

    @Test
    fun aDialogStatesAnOwnerOfItsOwnRatherThanItsWindowsOne() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val rootContent = LifecycleReader()
        val windowContent = LifecycleReader()
        val dialogContent = LifecycleReader()
        setContent {
            rootContent.Observe()
            Window(onCloseRequest = {}, title = OWN_OWNER_DIALOG_HOST_TITLE, visible = false) {
                windowContent.Observe()
                Dialog(onCloseRequest = {}, title = OWN_OWNER_DIALOG_TITLE, visible = false) {
                    dialogContent.Observe()
                }
            }
        }
        awaitIdle()

        val dialogOwner = assertNotNull(dialogContent.owner, "the dialog content must have read an owner")
        assertNotSame(
            assertNotNull(windowContent.owner, "the window content must have read an owner"),
            dialogOwner,
            "a dialog holds exactly the focus its owner window gives up, so its content must read an " +
                "owner of its own rather than the window's",
        )
        assertNotSame(
            assertNotNull(rootContent.owner, "the root content must have read an owner"),
            dialogOwner,
            "a dialog is a top-level window of its own, so its content must not read the owner of the " +
                "root the declaration was written in either",
        )
    }

    @Test
    fun aWindowStatesItsOwnOwnerOverOneProvidedAroundTheDeclaration() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // An owner provided from outside the library reaches the content composed under it, and stops at
        // a window declared there: that window's content stands in a top-level window of its own, whose
        // attachment, minimization and focus no owner but its own reports.
        val provided = ProvidedLifecycleOwner()
        val rootContent = LifecycleReader()
        val windowContent = LifecycleReader()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides provided) {
                rootContent.Observe()
                Window(onCloseRequest = {}, title = PROVIDED_OWNER_TITLE, visible = false) {
                    windowContent.Observe()
                }
            }
        }
        awaitIdle()

        assertSame(
            provided,
            rootContent.owner,
            "content composed under a provided owner must read that owner rather than the root's own",
        )
        assertNotSame(
            provided,
            windowContent.owner,
            "a window's content must read the owner of the window it is, not one provided around the " +
                "declaration it was written in",
        )
    }

    @Test
    fun theMenuBarAndOverlayOfAWindowAreAnsweredByThatWindowsOwner() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val windowContent = LifecycleReader()
        val menuContent = LifecycleReader()
        val overlayContent = LifecycleReader()
        // A menu bar and a glass pane are hosted in their own Swing containers, but they are parts of
        // the window the declaration is written in - which is what an owner of one window answers for.
        setContent {
            Window(onCloseRequest = {}, title = HOSTED_CONTENT_TITLE, visible = false) {
                windowContent.Observe()
                MenuBar { menuContent.Observe() }
                GlassPane { overlayContent.Observe() }
            }
        }
        awaitIdle()

        val windowOwner = assertNotNull(windowContent.owner, "the window content must have read an owner")
        assertSame(
            windowOwner,
            menuContent.owner,
            "a menu bar declared in a window must be answered by that window's own owner",
        )
        assertSame(
            windowOwner,
            overlayContent.owner,
            "an overlay declared in a window must be answered by that window's own owner",
        )
    }

    @Test
    fun anIslandHangingInAWindowReadsThatWindowsOwner() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val windowContent = LifecycleReader()
        val islandContent = LifecycleReader()
        var windowComposition: CompositionContext? = null
        var windowContentPane: Container? = null
        // A window's content publishes the owner that window was given, so an island hanging under it
        // reads that owner - one owner per window, however many islands the window hosts, and whichever
        // composition each of them joins.
        setContent {
            Window(onCloseRequest = {}, title = ISLAND_IN_WINDOW_TITLE, visible = false) {
                windowContent.Observe()
                windowComposition = rememberCompositionContext()
                windowContentPane = (LocalWindow.current as? RootPaneContainer)?.contentPane
            }
        }
        awaitIdle()

        val published = checkNotNull(windowComposition) { "the window content must have published its context" }
        val contentPane = checkNotNull(windowContentPane) { "the window content must have read its own window" }
        val island = JPanel().also { contentPane.add(it) }
        val handle = island.setContent(parent = published) { islandContent.Observe() }
        try {
            assertSame(
                assertNotNull(windowContent.owner, "the window content must have read an owner"),
                islandContent.owner,
                "an island hanging in a composed window must read that window's owner rather than one of its own",
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun anIslandReadsTheOwnerPublishedAboveItInTheSwingTree() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val hostContent = LifecycleReader()
        val islandContent = LifecycleReader()
        val host = JPanel()
        val hostHandle = host.setContent { hostContent.Observe() }
        val frame = frameHolding(WALKED_OWNER_TITLE, host)
        try {
            val hostOwner = assertNotNull(hostContent.owner, "the host content must have read an owner")

            // The island names the composition the window shares as its parent, which is no composition
            // root of the Swing tree and publishes nothing. What answers is where the island hangs: the
            // content composed above it published the owner it was given, for exactly this.
            val island = JPanel().also { host.add(it) }
            val islandHandle = island.setContent(parent = frame.compositionContext()) { islandContent.Observe() }
            try {
                assertSame(
                    hostOwner,
                    islandContent.owner,
                    "an island must read the owner published above it in the Swing tree",
                )
            } finally {
                islandHandle.dispose()
            }
        } finally {
            hostHandle.dispose()
            frame.dispose()
        }
    }

    @Test
    fun anIslandComposedAgainInAnotherWindowReadsTheOwnerStatedThere() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val firstRoot = LifecycleReader()
        val secondRoot = LifecycleReader()
        val islandContent = LifecycleReader()
        val firstHost = JPanel()
        val secondHost = JPanel()
        val firstHandle = firstHost.setContent { firstRoot.Observe() }
        val secondHandle = secondHost.setContent { secondRoot.Observe() }
        val first = frameHolding(REHOMED_FROM_TITLE, firstHost)
        val second = frameHolding(REHOMED_TO_TITLE, secondHost)
        val island = JPanel().also { firstHost.add(it) }
        val islandHandle = island.setContent { islandContent.Observe() }
        try {
            assertSame(
                assertNotNull(firstRoot.owner, "the content of the window it starts in must have read an owner"),
                islandContent.owner,
                "an island must read the owner published in the window it is composed in",
            )

            // Moving the container into another window composes its content again there, and the owner
            // that pass resolves is the one published in the window it arrived in.
            secondHost.add(island)
            awaitEventsDelivered()

            assertSame(
                assertNotNull(secondRoot.owner, "the content of the window it moves to must have read an owner"),
                islandContent.owner,
                "an island composed again in another window must read the owner published there",
            )
        } finally {
            islandHandle.dispose()
            firstHandle.dispose()
            secondHandle.dispose()
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun contentComposedAgainInAnotherWindowIsGivenALiveOwnerOfItsOwn() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // This container is its own root and hangs under nothing that publishes an owner, so every pass
        // mints one and publishes it right there. Moving the container composes it again, and that pass
        // must mint afresh rather than answer with the stamp the pass before it left: an owner a root
        // only read is never ended, so a root taking its own spent stamp for one it found would leave the
        // content reporting DESTROYED for good.
        val panel = JPanel()
        val reader = LifecycleReader()
        val handle = panel.setContent { reader.Observe() }
        val first = frameHolding(REMINTED_FROM_TITLE, panel)
        val second = frameHolding(REMINTED_TO_TITLE)
        try {
            first.pack()
            second.pack()
            awaitState(reader, Lifecycle.State.STARTED)
            val firstOwner = assertNotNull(reader.owner, "the content must have read an owner")

            second.contentPane.add(panel)
            awaitEventsDelivered()

            val secondOwner = assertNotNull(reader.owner, "the content composed again must have read an owner")
            assertNotSame(
                firstOwner,
                secondOwner,
                "content composed again must be given a fresh owner rather than the one the pass before it ended",
            )
            assertEquals(
                Lifecycle.State.DESTROYED,
                firstOwner.lifecycle.currentState,
                "the pass that is composed away must have ended the owner it minted",
            )

            awaitState(reader, Lifecycle.State.STARTED)
            assertEquals(
                Lifecycle.State.STARTED,
                reader.state,
                "content composed again in a realized window must be followed by a live owner",
            )

            handle.dispose()
            assertEquals(
                Lifecycle.State.DESTROYED,
                reader.state,
                "disposing the mount must end the owner its live pass minted",
            )
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun aWindowLeavingTheCompositionEndsItsOwnOwnerAtDestroyed() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var declared by mutableStateOf(true)
        val windowContent = LifecycleReader()
        setContent {
            if (declared) {
                Window(onCloseRequest = {}, title = LEAVING_TITLE, visible = false) {
                    windowContent.Observe()
                }
            }
        }
        awaitIdle()

        val state = assertNotNull(windowContent.state, "the window content must have read an owner")
        assertNotEquals(
            Lifecycle.State.DESTROYED,
            state,
            "a window's owner must still be live while the window is in the composition",
        )

        declared = false
        awaitIdle()

        assertEquals(
            Lifecycle.State.DESTROYED,
            windowContent.state,
            "a window leaving the composition must end its own owner at DESTROYED",
        )

        // The window is disposed once its own owner has ended, and that disposal takes the peer of the
        // content pane the owner follows with it - a move a live owner would report as a step back to
        // CREATED. Draining leaves nothing of it undelivered.
        awaitEventsDelivered()

        assertEquals(
            Lifecycle.State.DESTROYED,
            windowContent.state,
            "DESTROYED is terminal: the window's disposal must leave its owner where it is",
        )
    }

    @Test
    fun contentOfAWindowDeclaredUnderApplicationReadsAnOwner() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val windowContent = LifecycleReader()
        // An application composition is no composition root of the Swing tree and publishes no owner,
        // while reading LocalLifecycleOwner where none is published throws. The window stating one of
        // its own is what makes the declarative entry point readable at all.
        withTimeout(APPLICATION_TIMEOUT) {
            awaitApplication {
                Window(onCloseRequest = ::exitApplication, title = APPLICATION_TITLE, visible = false) {
                    windowContent.Observe()
                    LaunchedEffect(Unit) { exitApplication() }
                }
            }
        }

        assertNotNull(
            windowContent.owner,
            "content of a window declared under application { } must read a LifecycleOwner",
        )
        assertEquals(
            Lifecycle.State.DESTROYED,
            windowContent.state,
            "the application ending drops the window, which must end that window's owner at DESTROYED",
        )
    }
}

/** A [LifecycleOwner] standing in for one a caller or a navigation library provides. */
private class ProvidedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry
}

/**
 * Reads the [LifecycleOwner] of wherever [Observe] is composed, and the state that owner reports.
 *
 * The owner is held rather than its state read once, so a case can go on reading the state after the
 * composition that published the owner is gone - which is where DESTROYED is observed.
 */
private class LifecycleReader {
    var owner: LifecycleOwner? = null
        private set

    /** The state the owner reports, or `null` while nothing has composed. */
    val state: Lifecycle.State?
        get() = owner?.lifecycle?.currentState

    @Composable
    fun Observe() {
        owner = LocalLifecycleOwner.current
    }
}

/**
 * A [JFrame] that is not shown yet, sized so the window system can hand it the focus, holding [content].
 *
 * The content is added as the frame is built, which is the moment a `setContent` waiting for a window
 * mounts and composes - so a reader in that content has read its owner by the time this returns. Must
 * be called on the event dispatch thread.
 */
private fun frameHolding(
    title: String,
    content: Container = JPanel(),
): JFrame = JFrame(title).apply {
    defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
    contentPane.add(content)
    setSize(FRAME_SIDE, FRAME_SIDE)
}

/**
 * Shows this frame and asks the window system for the keyboard focus. Whether it is granted is the
 * window system's own decision, which the caller gates on.
 */
private fun JFrame.showAskingForFocus() {
    isVisible = true
    toFront()
    requestFocus()
}

/**
 * Suspends until [reader] reports [state], handing the event dispatch thread back between checks so the
 * hierarchy and window-system notifications the owner follows can be delivered.
 *
 * Returns at its deadline rather than failing, so the caller's own assertion reports the state the owner
 * actually holds instead of a wait that ran out. It sends no composition frame and needs none: an owner
 * answers for the Swing tree its content is in, not for the composition that content recomposes with.
 */
private suspend fun awaitState(
    reader: LifecycleReader,
    state: Lifecycle.State,
) {
    val deadline = TimeSource.Monotonic.markNow() + STATE_TIMEOUT
    while (reader.state != state && !deadline.hasPassedNow()) {
        delay(POLL_INTERVAL)
    }
}

/**
 * Suspends until [reader] reports [state] or a state above it, on the terms [awaitState] waits on.
 *
 * A window the window system restores from minimization is shown again, and whether it hands the
 * keyboard focus back with it is that window system's own decision - so what a case can hold such a
 * window to is the floor the two outcomes share, and the state above it is asserted where the case
 * turns the focus on itself.
 */
private suspend fun awaitStateAtLeast(
    reader: LifecycleReader,
    state: Lifecycle.State,
) {
    val deadline = TimeSource.Monotonic.markNow() + STATE_TIMEOUT
    while (reader.state?.isAtLeast(state) != true && !deadline.hasPassedNow()) {
        delay(POLL_INTERVAL)
    }
}

/** Distinct per case, so a window one case leaves behind cannot be mistaken for another case's. */
private const val UNFOCUSED_TITLE = "lifecycle-unfocused-window"
private const val FOCUSED_TITLE = "lifecycle-focused-window"
private const val FOCUS_MOVE_TITLE = "lifecycle-focus-move-window"
private const val FOCUS_STEALER_TITLE = "lifecycle-focus-stealer-window"
private const val DISPOSED_TITLE = "lifecycle-disposed-content-window"
private const val REPARENTED_TITLE = "lifecycle-reparented-window"
private const val MINIMIZED_TITLE = "lifecycle-minimized-window"
private const val OWN_OWNER_TITLE = "lifecycle-own-owner-window"
private const val OWN_OWNER_DIALOG_HOST_TITLE = "lifecycle-own-owner-dialog-host-window"
private const val OWN_OWNER_DIALOG_TITLE = "lifecycle-own-owner-dialog"
private const val PROVIDED_OWNER_TITLE = "lifecycle-provided-owner-window"
private const val HOSTED_CONTENT_TITLE = "lifecycle-hosted-content-window"
private const val LEAVING_TITLE = "lifecycle-leaving-window"
private const val APPLICATION_TITLE = "lifecycle-application-window"
private const val OWN_OWNER_DISPOSED_TITLE = "lifecycle-own-owner-disposed-window"
private const val ISLAND_IN_WINDOW_TITLE = "lifecycle-island-in-window"
private const val WALKED_OWNER_TITLE = "lifecycle-walked-owner-window"
private const val REHOMED_FROM_TITLE = "lifecycle-rehomed-from-window"
private const val REHOMED_TO_TITLE = "lifecycle-rehomed-to-window"
private const val REMINTED_FROM_TITLE = "lifecycle-reminted-from-window"
private const val REMINTED_TO_TITLE = "lifecycle-reminted-to-window"

/** Big enough to be a window the system can hand the focus to, small enough to stay out of the way. */
private const val FRAME_SIDE = 200

/**
 * Generous, for the reason the focus gate's own deadline is: a deadline that expires before the window
 * system has acted turns a state the owner does reach into a failure that says nothing about it, while a
 * case whose state arrives ends on its next check and pays none of it.
 */
private val STATE_TIMEOUT = 5.seconds
private val POLL_INTERVAL = 25.milliseconds

/** Bounds the application entry point, so an application that never ends fails rather than hanging. */
private val APPLICATION_TIMEOUT = 10.seconds
