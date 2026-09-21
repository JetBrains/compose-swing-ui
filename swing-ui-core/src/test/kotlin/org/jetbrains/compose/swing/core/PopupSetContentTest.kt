// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.assumeKeyboardFocusIsPossible
import org.jetbrains.compose.swing.assumeWindowBecomesFocused
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.LocalWindow
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager
import javax.swing.PopupFactory
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [JPopupMenu.setContent]: a popup is not a child of its invoker in the Swing tree,
 * so its content composes on the call under the parent the caller names.
 */
class PopupSetContentTest {
    @Test
    fun aPopupOutlastsTheOrdinaryContentOfItsNamedWindowAndIsDisposedWithTheWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        val popup = JPopupMenu()
        try {
            val ordinary = frame.setContent { Label("host") }
            val parent = assertNotNull(frame.findRecomposer())
            var text by mutableStateOf("before")
            popup.setContent(parent) { MenuItem(text = text, onClick = {}) }
            ordinary.dispose()
            awaitQueuedTurns()
            text = "after"
            awaitUntil("the popup recomposes after the window's ordinary content is disposed") {
                popup.menuItemTexts() == listOf("after")
            }
            frame.dispose()
            awaitUntil("closing the named window takes the popup content off the popup") {
                popup.menuItemTexts().isEmpty()
            }
        } finally {
            frame.dispose()
        }
    }

    @ExclusiveWindowSystem
    @TestFactory
    fun aPanelInsideAPopupIsDisposedWithTheNamedWindow(): List<DynamicTest> = listOf(
        PanelRow("hidden popup", shownBeforePanel = false, shownAfterPanel = false, expectShowing = false),
        PanelRow(
            "popup shown in another window before the panel composes",
            shownBeforePanel = true,
            shownAfterPanel = false,
            expectShowing = true,
        ),
        PanelRow(
            "popup shown in another window after the panel composes",
            shownBeforePanel = false,
            shownAfterPanel = true,
            expectShowing = true,
        ),
    ).map { row ->
        dynamicTest(row.name) {
            runSwingTest {
                assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
                val named = realizedFrame()
                val other =
                    realizedFrame().apply {
                        setSize(FRAME_SIDE, FRAME_SIDE)
                        isVisible = true
                    }
                val popup = JPopupMenu()
                var panelHandle: DisposableHandle? = null
                try {
                    val ordinary = named.setContent { Label("host") }
                    popup.setContent(assertNotNull(named.findRecomposer())) { MenuItem(text = "Cut", onClick = {}) }
                    if (row.shownBeforePanel) popup.show(other.contentPane, 0, 0)
                    val panel = JPanel().also { popup.add(it) }
                    panelHandle = panel.setContent { Label("inner") }
                    assertEquals(listOf("inner"), labelTexts(panel))
                    if (row.shownAfterPanel) popup.show(other.contentPane, 0, 0)
                    assertEquals(row.expectShowing, popup.isShowing)

                    ordinary.dispose()
                    named.dispose()
                    awaitUntil("closing the named window takes the panel's content off the panel") {
                        labelTexts(panel).isEmpty()
                    }
                } finally {
                    popup.isVisible = false
                    panelHandle?.dispose()
                    other.dispose()
                    named.dispose()
                }
            }
        }
    }

    @Test
    fun aPanelInsideThePopupJoinsItsParentUntilTheHandleIsDisposed() = runSwingTest {
        val test = TestRecomposer(this)
        val host = JPanel()
        var greeting by mutableStateOf("hello")
        var parent: CompositionContext? = null
        var parentOwner: LifecycleOwner? = null
        val hostHandle =
            host.setContent(test.recomposer) {
                CompositionLocalProvider(LocalGreeting provides greeting) {
                    parent = rememberCompositionContext()
                    parentOwner = LocalLifecycleOwner.current
                }
            }
        val popup = JPopupMenu()
        val popupHandle = popup.setContent(assertNotNull(parent)) { MenuItem(text = "Cut", onClick = {}) }
        val panel = JPanel().also { popup.add(it) }
        var panelOwner: LifecycleOwner? = null
        val panelHandle =
            panel.setContent {
                panelOwner = LocalLifecycleOwner.current
                Label(text = LocalGreeting.current)
            }
        try {
            assertEquals("hello", assertIs<JLabel>(panel.components.singleOrNull()).text)
            assertSame(assertNotNull(parentOwner), panelOwner, "the panel must read the parent's lifecycle owner")

            greeting = "updated"
            test.awaitIdle()
            assertEquals("updated", assertIs<JLabel>(panel.components.singleOrNull()).text)

            popupHandle.dispose()
            popup.assertSuppliesNoParent(test)
        } finally {
            panelHandle.dispose()
            popupHandle.dispose()
            hostHandle.dispose()
            test.cancel()
        }
    }

    @Test
    fun menuItemsComposedUnderTheParentReadItsCompositionLocals() = runSwingTest {
        val test = TestRecomposer(this)
        try {
            val host = JPanel()
            var parentContext: CompositionContext? = null
            val hostHandle =
                host.setContent(parent = test.recomposer) {
                    CompositionLocalProvider(LocalGreeting provides "hello from the parent") {
                        parentContext = rememberCompositionContext()
                    }
                }
            val popup = JPopupMenu()
            val popupHandle =
                popup.setContent(assertNotNull(parentContext)) { MenuItem(text = LocalGreeting.current, onClick = {}) }

            assertEquals(
                listOf("hello from the parent"),
                popup.menuItemTexts(),
                "a menu item composed under the parent must read the CompositionLocal provided above it",
            )

            popupHandle.dispose()
            hostHandle.dispose()
        } finally {
            test.cancel()
        }
    }

    @Test
    fun aDisposedHandleDoesNotReleaseTheContentThatReplacedIt() = runSwingTest {
        val test = TestRecomposer(this)
        try {
            val popup = JPopupMenu()
            val first = popup.setContent(test.recomposer) { MenuItem(text = "Cut", onClick = {}) }
            first.dispose()
            val second = popup.setContent(test.recomposer) { MenuItem(text = "Copy", onClick = {}) }
            assertEquals(listOf("Copy"), popup.menuItemTexts())

            first.dispose()
            assertEquals(listOf("Copy"), popup.menuItemTexts())
            assertFailsWith<IllegalStateException> {
                popup.setContent(test.recomposer) { MenuItem(text = "Paste", onClick = {}) }
            }
            second.dispose()
        } finally {
            test.cancel()
        }
    }

    @Test
    fun aParentWhoseRecomposerIsShutDownIsRefused() = runSwingTest {
        val test = TestRecomposer(this)
        try {
            val popup = JPopupMenu()
            val recomposer = SwingRecomposer.create(popup)
            val shutDown = recomposer.compositionContext
            recomposer.dispose()

            assertFailsWith<IllegalArgumentException> {
                popup.setContent(shutDown) { MenuItem(text = "never composes", onClick = {}) }
            }
            assertEquals(emptyList(), popup.menuItemTexts(), "a refused call must compose nothing")

            popup.setContent(test.recomposer) { MenuItem(text = "Cut", onClick = {}) }.dispose()
        } finally {
            test.cancel()
        }
    }

    @TestFactory
    fun disposingTheHandleEmptiesThePopupAndClearsTheSelectionFromIt(): List<DynamicTest> = listOf(
        "a standalone popup" to false,
        "a menu's popup under a menu bar" to true,
    ).map { (name, underMenuBar) ->
        dynamicTest(name) {
            runSwingTest {
                val test = TestRecomposer(this)
                val selection = MenuSelectionManager.defaultManager()
                try {
                    val bar = JMenuBar()
                    val menu = JMenu("Edit")
                    bar.add(menu)
                    val popup = if (underMenuBar) menu.popupMenu else JPopupMenu()
                    val handle = popup.setContent(test.recomposer) { MenuItem(text = "Cut", onClick = {}) }
                    val ancestors = if (underMenuBar) listOf<MenuElement>(bar, menu) else emptyList()
                    val item = popup.components.single() as MenuElement
                    selection.selectedPath = (ancestors + popup + item).toTypedArray()

                    handle.dispose()

                    assertEquals(emptyList(), popup.menuItemTexts())
                    assertEquals(
                        ancestors,
                        selection.selectedPath.toList(),
                        "only the popup's ancestors must stay selected",
                    )
                } finally {
                    selection.clearSelectedPath()
                    test.cancel()
                }
            }
        }
    }

    @Test
    fun disposingTheHandleKeepsAPanelTheCallerAdded() = runSwingTest {
        val test = TestRecomposer(this)
        try {
            val popup = JPopupMenu()
            val handle = popup.setContent(test.recomposer) { MenuItem(text = "Cut", onClick = {}) }
            val panel = JPanel().also { popup.add(it) }
            val panelHandle = panel.setContent { Label(text = "inside") }

            handle.dispose()

            assertEquals(listOf(panel), popup.components.toList(), "the caller's panel must stay on the popup")
            assertEquals("inside", assertIs<JLabel>(panel.components.singleOrNull()).text)
            panelHandle.dispose()
        } finally {
            test.cancel()
        }
    }

    @Test
    fun contentUnderABareRecomposerReadsAnOwnerThePopupCreatesAndDestroysWithTheHandle() = runSwingTest {
        val popup = JPopupMenu()
        val recomposer = SwingRecomposer.create(popup)
        try {
            var owner: LifecycleOwner? = null
            val handle =
                popup.setContent(recomposer.compositionContext) {
                    owner = LocalLifecycleOwner.current
                    MenuItem(text = "Cut", onClick = {})
                }
            val read = assertNotNull(owner, "popup content must have a lifecycle owner even under a recomposer root")
            assertEquals(
                Lifecycle.State.CREATED,
                read.lifecycle.currentState,
                "a recomposer root states no owner, so the content reads the one the popup creates, which is " +
                    "CREATED while the popup is in no window",
            )

            handle.dispose()

            assertEquals(
                Lifecycle.State.DESTROYED,
                read.lifecycle.currentState,
                "disposing the handle must destroy the owner the content read",
            )
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun popupItemsFollowStateChangesAndRemovals() = runComposeSwingTest {
        var text by mutableStateOf("Cut")
        var showCopy by mutableStateOf(true)
        var parent: CompositionContext? = null
        setContent { parent = rememberCompositionContext() }
        val popup = JPopupMenu()
        val handle =
            popup.setContent(assertNotNull(parent)) {
                MenuItem(text = text, onClick = {})
                if (showCopy) MenuItem(text = "Copy", onClick = {})
            }
        try {
            assertEquals(listOf("Cut", "Copy"), popup.menuItemTexts())

            text = "Paste"
            awaitIdle()
            assertEquals(listOf("Paste", "Copy"), popup.menuItemTexts())

            showCopy = false
            awaitIdle()
            assertEquals(listOf("Paste"), popup.menuItemTexts())
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun theParentKeepsComposingAfterThePopupIsDisposed() = runComposeSwingTest {
        var text by mutableStateOf("before")
        var parent: CompositionContext? = null
        setContent {
            parent = rememberCompositionContext()
            Label(text = text)
        }
        val popup = JPopupMenu()
        popup.setContent(assertNotNull(parent)) { MenuItem(text = text, onClick = {}) }.dispose()

        text = "after"
        awaitIdle()
        onNodeOfType<JLabel>().assertTextEquals("after")
    }

    @Test
    fun aWindowCompositionPassesItsWindowAndLifecycleOwnerToPopupItems() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            var parent: CompositionContext? = null
            var parentOwner: LifecycleOwner? = null
            val host =
                frame.setContent {
                    parent = rememberCompositionContext()
                    parentOwner = LocalLifecycleOwner.current
                }
            val popup = JPopupMenu()
            var itemWindow: Window? = null
            var itemOwner: LifecycleOwner? = null
            val handle =
                popup.setContent(assertNotNull(parent)) {
                    itemWindow = LocalWindow.current
                    itemOwner = LocalLifecycleOwner.current
                    MenuItem(text = "Cut", onClick = {})
                }
            assertSame(frame, itemWindow)
            assertSame(assertNotNull(parentOwner), itemOwner)
            handle.dispose()
            host.dispose()
        } finally {
            frame.dispose()
        }
    }

    @ExclusiveWindowSystem
    @TestFactory
    fun itemsUnderARecomposerRootReadTheWindowOfThePopupsInvoker(): List<DynamicTest> = listOf(
        "lightweight popup" to true,
        "heavyweight popup" to false,
    ).map { (name, lightweight) ->
        dynamicTest(name) {
            runSwingTest {
                assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
                val test = TestRecomposer(this)
                val frame =
                    realizedFrame().apply {
                        setSize(FRAME_SIDE, FRAME_SIDE)
                        isVisible = true
                    }
                // Read after the frame, which is what initializes the look and feel: it may install a factory
                // that makes every popup heavyweight, where the plain one honors isLightWeightPopupEnabled.
                val installedFactory = PopupFactory.getSharedInstance()
                PopupFactory.setSharedInstance(PopupFactory())
                try {
                    val popup = JPopupMenu().apply { isLightWeightPopupEnabled = lightweight }
                    var itemWindow: Window? = null
                    val handle =
                        popup.setContent(test.recomposer) {
                            itemWindow = LocalWindow.current
                            MenuItem(text = "Close", onClick = {})
                        }
                    assertNull(itemWindow, "a popup that was never shown must state no window")

                    popup.show(frame.contentPane, 0, 0)
                    test.awaitIdle()
                    assertTrue(popup.isShowing, "the popup must be on screen")
                    assertEquals(
                        lightweight,
                        SwingUtilities.getWindowAncestor(popup) === frame,
                        "a lightweight popup sits in its invoker's window and a heavyweight one in a window of its own",
                    )
                    assertSame(frame, itemWindow, "the items must read the window of the popup's invoker")

                    popup.isVisible = false
                    test.awaitIdle()
                    assertSame(frame, itemWindow, "a hidden popup's items must keep the window of its invoker")
                    handle.dispose()
                } finally {
                    frame.dispose()
                    PopupFactory.setSharedInstance(installedFactory)
                    test.cancel()
                }
            }
        }
    }

    @ExclusiveWindowSystem
    @TestFactory
    fun popupContentUnderANestedContextIsDisposedWithTheWindowOfItsInvoker(): List<DynamicTest> = listOf(
        InvokerRow("lightweight popup over one window", lightweight = true, movesToSecondWindow = false),
        InvokerRow("heavyweight popup over one window", lightweight = false, movesToSecondWindow = false),
        InvokerRow("lightweight popup moved to a second window", lightweight = true, movesToSecondWindow = true),
        InvokerRow("heavyweight popup moved to a second window", lightweight = false, movesToSecondWindow = true),
    ).map { row ->
        dynamicTest(row.name) {
            runSwingTest {
                assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
                assertNestedPopupContentFollowsItsInvoker(row)
            }
        }
    }

    @ExclusiveWindowSystem
    @Test
    fun aPanelInAShownHeavyweightPopupReadsTheFocusOfItsInvokersWindow() {
        assumeKeyboardFocusIsPossible()
        runSwingTest {
            val frame = shownFrame()
            val content = frame.setContent { Label("host") }
            val installedFactory = PopupFactory.getSharedInstance()
            PopupFactory.setSharedInstance(PopupFactory())
            val popup = JPopupMenu().apply { isLightWeightPopupEnabled = false }
            val popupHandle = popup.setContent(assertNotNull(frame.findRecomposer())) { MenuItem("Cut", onClick = {}) }
            val panel = JPanel().also { popup.add(it) }
            var panelOwner: LifecycleOwner? = null
            val panelHandle =
                panel.setContent {
                    panelOwner = LocalLifecycleOwner.current
                    Label("inside")
                }
            try {
                frame.toFront()
                frame.requestFocus()
                assumeWindowBecomesFocused(frame)

                popup.show(frame.contentPane, 0, 0)

                val owner = assertNotNull(panelOwner)
                awaitUntil("the panel in the popup reads the focused window of the popup's invoker") {
                    owner.lifecycle.currentState == Lifecycle.State.RESUMED
                }
                assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
            } finally {
                popup.isVisible = false
                panelHandle.dispose()
                popupHandle.dispose()
                content.dispose()
                frame.dispose()
                PopupFactory.setSharedInstance(installedFactory)
            }
        }
    }

    @Test
    fun aThrowingDisposeEmptiesAndFreesThePopup() = runSwingTest {
        val test = TestRecomposer(this)
        try {
            val popup = JPopupMenu()
            val handle =
                popup.setContent(test.recomposer) {
                    MenuItem(text = "Cut", onClick = {})
                    DisposableEffect(Unit) { onDispose { throw DisposeFailure() } }
                }

            assertFailsWith<DisposeFailure> { handle.dispose() }
            assertEquals(
                emptyList(),
                popup.menuItemTexts(),
                "a throwing composition dispose must still empty the popup",
            )
            popup.assertSuppliesNoParent(test)
            popup.setContent(test.recomposer) { MenuItem(text = "Copy", onClick = {}) }.dispose()
        } finally {
            test.cancel()
        }
    }

    /** Asserts that a panel added to this popup composes nothing from a `setContent` naming no parent. */
    private suspend fun JPopupMenu.assertSuppliesNoParent(test: TestRecomposer) {
        val panel = JPanel().also { add(it) }
        val handle = panel.setContent { Label("inner") }
        try {
            test.awaitIdle()
            assertEquals(
                emptyList(),
                labelTexts(panel),
                "a popup with no undisposed content must supply no parent to a panel inside it",
            )
        } finally {
            handle.dispose()
            remove(panel)
        }
    }

    /**
     * Shows a popup whose content composes under a nested context over [row]'s windows and closes them: the
     * content must be disposed with the window of the popup's invoker, and not before.
     */
    private suspend fun CoroutineScope.assertNestedPopupContentFollowsItsInvoker(row: InvokerRow) {
        val test = TestRecomposer(this)
        val first = shownFrame()
        val second = shownFrame()
        first.setContent { Label("first") }
        second.setContent { Label("second") }
        // Read after the frames, which initialize the look and feel: it may install a factory that
        // makes every popup heavyweight, where the plain one honors isLightWeightPopupEnabled.
        val installedFactory = PopupFactory.getSharedInstance()
        PopupFactory.setSharedInstance(PopupFactory())
        var nested: CompositionContext? = null
        val hostHandle = JPanel().setContent(parent = test.recomposer) { nested = rememberCompositionContext() }
        val popup = JPopupMenu().apply { isLightWeightPopupEnabled = row.lightweight }
        var text by mutableStateOf("before")
        val handle = popup.setContent(assertNotNull(nested)) { MenuItem(text = text, onClick = {}) }
        try {
            popup.show(first.contentPane, 0, 0)
            if (row.movesToSecondWindow) {
                popup.isVisible = false
                popup.show(second.contentPane, 0, 0)
                awaitQueuedTurns()
            }
            assertTrue(popup.isShowing, "the popup must be on screen")

            first.dispose()
            awaitUntil("the first window's teardown has run") { first.swingRecomposerOrNull() == null }
            if (row.movesToSecondWindow) {
                text = "after"
                test.awaitIdle()
                assertEquals(
                    listOf("after"),
                    popup.menuItemTexts(),
                    "a popup now shown over the second window must outlast the first window",
                )
                second.dispose()
            }
            awaitUntil("closing the window of the popup's invoker takes the content off the popup") {
                popup.menuItemTexts().isEmpty()
            }
        } finally {
            popup.isVisible = false
            handle.dispose()
            hostHandle.dispose()
            first.dispose()
            second.dispose()
            PopupFactory.setSharedInstance(installedFactory)
            test.cancel()
        }
    }

    /** A shown frame, big enough for a popup to open over. Must be called on the EDT. */
    private fun shownFrame(): JFrame = realizedFrame().apply {
        setSize(FRAME_SIDE, FRAME_SIDE)
        isVisible = true
    }

    private companion object {
        val LocalGreeting = compositionLocalOf { "no parent" }
        const val FRAME_SIDE = 400
    }
}

private class PanelRow(
    val name: String,
    val shownBeforePanel: Boolean,
    val shownAfterPanel: Boolean,
    val expectShowing: Boolean,
)

private class InvokerRow(
    val name: String,
    val lightweight: Boolean,
    val movesToSecondWindow: Boolean,
)

/** Thrown by content under test, so a test asserts the failure by its type rather than by its message. */
private class DisposeFailure : RuntimeException()
