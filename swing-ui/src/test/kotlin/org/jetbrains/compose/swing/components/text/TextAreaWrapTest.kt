// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertSame

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

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(true) { lineWrap }
        area.assertProperty(true) { wrapStyleWord }
    }

    @Test
    fun lineWrapFollowsRecomposition() = runComposeSwingTest {
        var lineWrap by mutableStateOf(true)
        setContent { TextArea(value = "a long line of prose", onValueChange = {}, lineWrap = lineWrap) }

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(true) { this.lineWrap }
        val areaBefore = onNodeOfType<JTextArea>().fetch()

        lineWrap = false
        awaitIdle()
        area.assertProperty(false) { this.lineWrap }

        lineWrap = true
        awaitIdle()
        assertSame(areaBefore, onNodeOfType<JTextArea>().fetch(), "the component is updated in place, not rebuilt")
        area.assertProperty(true) { this.lineWrap }
    }

    @Test
    fun wrapStyleWordFollowsRecompositionIndependentlyOfLineWrap() = runComposeSwingTest {
        var wrapStyleWord by mutableStateOf(true)
        setContent { TextArea(value = "a long line of prose", onValueChange = {}, wrapStyleWord = wrapStyleWord) }

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(true) { this.wrapStyleWord }
        area.assertProperty(false, "wrapStyleWord must not switch wrapping on") { lineWrap }
        val areaBefore = onNodeOfType<JTextArea>().fetch()

        wrapStyleWord = false
        awaitIdle()
        assertSame(areaBefore, onNodeOfType<JTextArea>().fetch(), "the component is updated in place, not rebuilt")
        area.assertProperty(false) { this.wrapStyleWord }
    }

    @Test
    fun lineWrapDrivesScrollableWidthTracking() = runComposeSwingTest {
        var lineWrap by mutableStateOf(true)
        setContent { TextArea(value = "a long line of prose", onValueChange = {}, lineWrap = lineWrap) }

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(
            true,
            "a wrapping area tracks its viewport's width instead of scrolling sideways",
        ) { scrollableTracksViewportWidth }
        val areaBefore = onNodeOfType<JTextArea>().fetch()

        lineWrap = false
        awaitIdle()
        assertSame(areaBefore, onNodeOfType<JTextArea>().fetch(), "the component is updated in place, not rebuilt")
        area.assertProperty(false) { scrollableTracksViewportWidth }
    }

    @Test
    fun wrappingParametersReachTheStateDrivenArea() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("a long line of prose")
            TextArea(state = state, lineWrap = true, wrapStyleWord = true)
        }

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(true) { lineWrap }
        area.assertProperty(true) { wrapStyleWord }
    }
}
