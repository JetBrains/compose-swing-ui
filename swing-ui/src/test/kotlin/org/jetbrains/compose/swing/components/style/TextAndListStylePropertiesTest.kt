// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.style.SetterCase
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.Color
import javax.swing.JTextArea
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter
import kotlin.test.Test

class TextAndListStylePropertiesTest {
    @Test
    fun declaredRangesArePainted() = runComposeSwingTest {
        setContent {
            TextArea("hello world", {}, style = {
                highlights(listOf(TextRange(0, 5), TextRange(6, 11)), HighlightPainter)
            })
        }
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(0, 5), TextRange(6, 11))) {
            marksOf(HighlightPainter)
        }
    }

    @Test
    fun highlightsUpdateAndRemoveOnlyOwnedMarks() = runComposeSwingTest {
        val ranges = mutableStateListOf(TextRange(0, 2))
        var declared by mutableStateOf(false)
        var painter by mutableStateOf<Highlighter.HighlightPainter>(HighlightPainter)
        setContent {
            TextArea("hello world", {}, style = { if (declared) highlights(ranges, painter) })
        }
        onNodeOfType<JTextArea>().assertProperty(emptyList()) { marksOf(HighlightPainter) }
        declared = true
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(0, 2))) { marksOf(HighlightPainter) }
        ranges[0] = TextRange(1, 3)
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(1, 3))) { marksOf(HighlightPainter) }
        declared = false
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(emptyList()) { marksOf(HighlightPainter) }
        ranges[0] = TextRange(0, 5)
        declared = true
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(0, 5))) { marksOf(HighlightPainter) }
        val foreignPainter = DefaultHighlighter.DefaultHighlightPainter(Color.PINK)
        onNodeOfType<JTextArea>().fetch().highlighter.apply {
            addHighlight(2, 4, foreignPainter)
            addHighlight(0, 1, foreignPainter)
        }

        ranges.add(TextRange(6, 11))
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(0, 5), TextRange(6, 11))) {
            marksOf(HighlightPainter)
        }

        ranges.removeAt(0)
        awaitIdle()
        onNodeOfType<JTextArea>().apply {
            assertProperty(listOf(TextRange(6, 11))) { marksOf(HighlightPainter) }
            assertProperty(listOf(TextRange(2, 4), TextRange(0, 1))) { marksOf(foreignPainter) }
        }

        ranges.clear()
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(emptyList()) { marksOf(HighlightPainter) }
        ranges.addAll(listOf(TextRange(0, 5), TextRange(6, 11)))
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(0, 5), TextRange(6, 11))) {
            marksOf(HighlightPainter)
        }

        painter = foreignPainter
        awaitIdle()
        onNodeOfType<JTextArea>().apply {
            assertProperty(listOf(TextRange(2, 4), TextRange(0, 1), TextRange(0, 5), TextRange(6, 11))) {
                marksOf(foreignPainter)
            }
            assertProperty(emptyList()) { marksOf(HighlightPainter) }
        }

        declared = false
        awaitIdle()
        onNodeOfType<JTextArea>().apply {
            assertProperty(emptyList()) { marksOf(HighlightPainter) }
            assertProperty(listOf(TextRange(2, 4), TextRange(0, 1))) { marksOf(foreignPainter) }
        }
    }

    @ParameterizedTest
    @CsvSource("11, 6, 6, 11", "6, 400, 6, 11")
    fun rangesNormalizeAndClamp(
        start: Int,
        end: Int,
        expectedStart: Int,
        expectedEnd: Int,
    ) = runComposeSwingTest {
        setContent {
            TextArea(
                "hello world",
                {},
                style = { highlights(listOf(TextRange(start, end)), HighlightPainter) },
            )
        }
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(expectedStart, expectedEnd))) {
            marksOf(HighlightPainter)
        }
    }

    @Test
    fun aDeclarationFollowsTheAreaOntoTheDocumentItIsReboundTo() = runComposeSwingTest {
        var rebound by mutableStateOf(false)
        setContent {
            val first = rememberDocumentState("hello world")
            val second = rememberDocumentState("hello world")
            TextArea(
                state = if (rebound) second else first,
                style = { highlights(listOf(TextRange(0, 5)), HighlightPainter) },
            )
        }
        val bound = onNodeOfType<JTextArea>().fetch().document
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(0, 5))) { marksOf(HighlightPainter) }
        rebound = true
        awaitIdle()
        onNodeOfType<JTextArea>().apply {
            assertProperty(true) { document !== bound }
            assertProperty(listOf(TextRange(0, 5))) { marksOf(HighlightPainter) }
        }
    }

    @Test
    fun aDeclarationSurvivesAWriteOfTheDeclaredText() = runComposeSwingTest {
        var text by mutableStateOf("hello world")
        setContent {
            // The painter is hoisted, so a pass that only changes the text declares an equal style.
            TextArea(text, {}, style = { highlights(listOf(TextRange(6, 11)), HighlightPainter) })
        }
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(6, 11))) { marksOf(HighlightPainter) }
        text = "hello there"
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(6, 11))) { marksOf(HighlightPainter) }
    }

    @Test
    fun aDeclarationStaysWhereItSaysWhenTheTextBeforeItGrows() = runComposeSwingTest {
        var text by mutableStateOf("hello world")
        setContent {
            TextArea(text, {}, style = { highlights(listOf(TextRange(6, 11)), HighlightPainter) })
        }
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(6, 11))) { marksOf(HighlightPainter) }
        text = "xhello world"
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(6, 11))) { marksOf(HighlightPainter) }
    }
}

internal inline fun <reified V : Any> textCase(
    name: String,
    first: V,
    second: V,
    noinline read: (JTextArea) -> Any?,
    noinline set: TextStyleScope.(V) -> Unit,
): SetterCase = SetterCase(name, first, second, { onNodeOfType<JTextArea>() }, { read(it as JTextArea) }) { value ->
    TextArea("hello world", {}, style = { (value() as V?)?.let { set(it) } })
}

private fun JTextArea.marksOf(painter: Highlighter.HighlightPainter): List<TextRange> =
    highlighter.highlights.filter { it.painter === painter }.map { TextRange(it.startOffset, it.endOffset) }

private val HighlightPainter = DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
