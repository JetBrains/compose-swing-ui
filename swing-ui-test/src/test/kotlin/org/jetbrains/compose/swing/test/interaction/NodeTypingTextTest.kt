// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextArea
import javax.swing.JTextField
import kotlin.test.Test

/**
 * Pins what the text gestures carry. [performTyping] delivers keystrokes, so the component's own key
 * bindings decide what each one does; [performTextPaste] runs none of them.
 */
class NodeTypingTextTest {
    @Test
    fun typingCarriesEveryCharacterAnEditorAccepts() = runComposeSwingTest {
        setContent { AdoptingField() }

        // Accents precomposed and as combining marks, a script written right to left, and characters
        // outside the Basic Multilingual Plane, which the toolkit carries as their two UTF-16 units.
        val text = "café / café / שלום / 日本語 / a😀b"
        onNodeOfType<JTextField>().performTyping(text)

        onNodeOfType<JTextField>().assertProperty(
            text,
            "typing must carry the text through the editor unchanged, whatever plane or script it is in",
        ) { this.text }
    }

    @Test
    fun typingRunsAKeyBindingRatherThanInsertingItsCharacter() = runComposeSwingTest {
        setContent { AdoptingField() }

        // A single-line field binds Tab to focus traversal and Enter to its own action, so neither
        // keystroke reaches the document.
        onNodeOfType<JTextField>().performTyping("a\tb\nc")

        onNodeOfType<JTextField>().assertProperty(
            "abc",
            "a key the component binds an action to runs it rather than inserting its character",
        ) { text }
    }

    @Test
    fun typingInsertsTheSameCharactersWhereTheComponentBindsThemToInserts() = runComposeSwingTest {
        setContent { AdoptingArea() }

        // The same keystrokes as above: a text area binds Tab and Enter to insert actions, so both
        // land. What a keystroke does is the component's business, here as it is for a user.
        onNodeOfType<JTextArea>().performTyping("a\tb\nc")

        onNodeOfType<JTextArea>().assertProperty(
            "a\tb\nc",
            "a text area types the tab and the newline a single-line field runs a binding for",
        ) { text }
    }

    @Test
    fun pastingCarriesContentTheComponentsKeysWillNotProduce() = runComposeSwingTest {
        setContent { AdoptingField() }

        // A paste runs no key binding, so the tab a keystroke could not deliver lands. The document
        // still applies its own rules, and a single-line field keeps no newline.
        onNodeOfType<JTextField>().performTextPaste("a\tb\nc")

        onNodeOfType<JTextField>().assertProperty(
            "a\tb c",
            "a paste carries the tab and the field replaces the newline with a space",
        ) { text }
    }

    @Test
    fun writingStraightToTheComponentBypassesInputHandlingAltogether() = runComposeSwingTest {
        setContent { AdoptingArea() }

        // fetch hands back the widget itself, so a test that wants content no gesture can produce puts
        // it there directly. Nothing about input is exercised by this - it is the way out of the
        // gestures, not one of them.
        onNodeOfType<JTextArea>().fetch<JTextArea>().text = "one\ttwo\nthree"
        awaitIdle()

        onNodeOfType<JTextArea>().assertProperty(
            "one\ttwo\nthree",
            "a write straight to the component is kept when the caller adopts it",
        ) { text }
    }

    @Test
    fun pastingReplacesTheSelectionAsAPasteDoes() = runComposeSwingTest {
        setContent { AdoptingArea() }

        onNodeOfType<JTextArea>().performTyping("abc")
        onNodeOfType<JTextArea>().fetch<JTextArea>().selectAll()
        onNodeOfType<JTextArea>().performTextPaste("z")

        onNodeOfType<JTextArea>().assertProperty(
            "z",
            "a paste lands over whatever is selected",
        ) { text }
    }
}

/** Adopts edits, including lines that keystrokes cannot insert. */
@Composable
private fun AdoptingArea() {
    var typed by remember { mutableStateOf("") }
    TextArea(value = typed, onValueChange = { typed = it })
}

/** Adopts every edit the editor accepts. */
@Composable
private fun AdoptingField() {
    var typed by remember { mutableStateOf("") }
    TextField(value = typed, onValueChange = { typed = it })
}
