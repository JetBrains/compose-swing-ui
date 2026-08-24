package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for [TextArea]'s two wrapping parameters. They are independent: `lineWrap` decides
 * whether an over-long line continues on the next one, `wrapStyleWord` only picks where such a line is
 * broken. Wrapping is observable through the area's scrollable width tracking, which is what makes an
 * area inside a scroll pane stop scrolling horizontally.
 */
class TextAreaWrapTest {
    @Test
    fun wrappingParametersReachTheArea() = runComposeSwingTest {
        setContent {
            TextArea(value = "a long line of prose", onValueChange = {}, lineWrap = true, wrapStyleWord = true)
        }

        val area = onNodeOfType<JTextArea>().fetch()
        assertTrue(area.lineWrap)
        assertTrue(area.wrapStyleWord)
    }

    @Test
    fun lineWrapFollowsRecomposition() = runComposeSwingTest {
        var lineWrap by mutableStateOf(true)
        setContent { TextArea(value = "a long line of prose", onValueChange = {}, lineWrap = lineWrap) }

        val area = onNodeOfType<JTextArea>().fetch()
        assertTrue(area.lineWrap)

        lineWrap = false
        awaitIdle()
        assertFalse(area.lineWrap)

        lineWrap = true
        awaitIdle()
        assertTrue(area.lineWrap)
    }

    @Test
    fun wrapStyleWordFollowsRecompositionIndependentlyOfLineWrap() = runComposeSwingTest {
        var wrapStyleWord by mutableStateOf(true)
        setContent { TextArea(value = "a long line of prose", onValueChange = {}, wrapStyleWord = wrapStyleWord) }

        val area = onNodeOfType<JTextArea>().fetch()
        assertTrue(area.wrapStyleWord)
        assertFalse(area.lineWrap, "wrapStyleWord must not switch wrapping on")

        wrapStyleWord = false
        awaitIdle()
        assertFalse(area.wrapStyleWord)
    }

    @Test
    fun lineWrapDrivesScrollableWidthTracking() = runComposeSwingTest {
        var lineWrap by mutableStateOf(true)
        setContent { TextArea(value = "a long line of prose", onValueChange = {}, lineWrap = lineWrap) }

        val area = onNodeOfType<JTextArea>().fetch()
        assertTrue(
            area.scrollableTracksViewportWidth,
            "a wrapping area tracks its viewport's width instead of scrolling sideways",
        )

        lineWrap = false
        awaitIdle()
        assertFalse(area.scrollableTracksViewportWidth)
    }

    @Test
    fun wrappingParametersReachTheStateDrivenArea() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("a long line of prose")
            TextArea(state = state, lineWrap = true, wrapStyleWord = true)
        }

        val area = onNodeOfType<JTextArea>().fetch()
        assertTrue(area.lineWrap)
        assertTrue(area.wrapStyleWord)
    }
}
