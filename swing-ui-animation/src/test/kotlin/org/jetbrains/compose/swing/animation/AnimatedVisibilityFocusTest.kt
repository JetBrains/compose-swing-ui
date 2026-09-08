package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A field that records where each request to pass focus on would land, rather than moving focus itself, so a
 * test takes no focus from the machine it runs on.
 */
private class FocusRecordingField : JTextField() {
    val transfers = ArrayList<Component?>()

    override fun transferFocus() {
        val root = focusCycleRootAncestor
        transfers += root?.focusTraversalPolicy?.getComponentAfter(root, this)
    }
}

/** Runs [block] while the keyboard focus manager reports [owner] as the focus owner, without asking for focus. */
private inline fun reportingFocusOwner(
    owner: Component,
    block: () -> Unit,
) {
    val standing = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    KeyboardFocusManager.setCurrentKeyboardFocusManager(
        object : DefaultKeyboardFocusManager() {
            override fun getFocusOwner(): Component = owner
        },
    )
    try {
        block()
    } finally {
        KeyboardFocusManager.setCurrentKeyboardFocusManager(standing)
    }
}

private const val FOCUS_WINDOW = "exit-focus-owner"

class AnimatedVisibilityFocusTest {
    @Test
    fun `a focus owner inside exiting content passes focus past the content as the exit begins`() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val owner = FocusRecordingField()
            var visible by mutableStateOf(true)
            setContent {
                Window(onCloseRequest = {}, title = FOCUS_WINDOW) {
                    Panel(PanelLayout.Flow()) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = EnterTransition.None,
                            exit = fadeOut(animationSpec = tween(160)),
                        ) {
                            Row {
                                SwingNode(factory = { owner })
                                TextField("", onValueChange = {}, modifier = SwingModifier.testTag("inside"))
                            }
                        }
                        TextField("", onValueChange = {}, modifier = SwingModifier.testTag("after"))
                    }
                }
            }
            awaitIdle()
            val window = onWindowWithTitle(FOCUS_WINDOW)
            val root = owner.focusCycleRootAncestor
            assertSame(
                window.onNodeWithTag("inside").fetch(),
                root.focusTraversalPolicy.getComponentAfter(root, owner),
                "precondition: a tab from the focus owner lands inside the content",
            )

            mainClock.autoAdvance = false
            reportingFocusOwner(owner) {
                visible = false
                driveOneFrame()
            }

            assertEquals(
                listOf<Component?>(window.onNodeWithTag("after").fetch()),
                owner.transfers,
                "the focus owner inside the exiting content did not pass focus past that content",
            )
        }

    @Test
    fun `a focus owner outside exiting content keeps the focus`() =
        runComposeSwingTest {
            val owner = FocusRecordingField()
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = EnterTransition.None,
                    exit = fadeOut(animationSpec = tween(160)),
                ) {
                    Block()
                }
                SwingNode(factory = { owner })
            }

            mainClock.autoAdvance = false
            reportingFocusOwner(owner) {
                visible = false
                driveOneFrame()
            }

            assertEquals(
                emptyList<Component?>(),
                owner.transfers,
                "a focus owner outside the exiting content passed focus on",
            )
        }
}
