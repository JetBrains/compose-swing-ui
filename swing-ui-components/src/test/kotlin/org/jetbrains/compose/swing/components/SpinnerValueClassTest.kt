// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSpinner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The class a number spinner's editor parses commits back to. A `JSpinner.NumberEditor` takes it from
 * the value its model holds as it is built, so a spinner whose declared value changes class needs the
 * editor built again - one built for an `Int` would commit 2 for a typed 2.5.
 */
class SpinnerValueClassTest {
    @Test
    fun aCommitKeepsTheDecimalsOfAValueDeclaredAfterAnInt() = runComposeSwingTest {
        var value by mutableStateOf<Number>(1)
        setContent { Spinner(value = value, onValueChange = { value = it }, step = 0.5) }

        val spinner = onNodeOfType<JSpinner>().fetch<JSpinner>()
        value = 2.5
        awaitIdle()
        assertEquals(2.5, spinner.value, "the spinner should show the declared value")

        // Typed as the editor renders it, so the assertion is about the class a commit parses back to
        // rather than about the locale's decimal separator.
        val field = (spinner.editor as JSpinner.DefaultEditor).textField
        field.text = field.formatter.valueToString(3.5)
        field.commitEdit()
        awaitIdle()

        assertEquals(3.5, spinner.value, "a commit should keep the decimals the editor shows")
        assertEquals(3.5, value, "and be reported as what was typed")
    }

    @Test
    fun aCommitDropsTheDecimalsOfAnIntDeclaredAfterADouble() = runComposeSwingTest {
        var value by mutableStateOf<Number>(2.5)
        setContent { Spinner(value = value, onValueChange = { value = it }) }

        val spinner = onNodeOfType<JSpinner>().fetch<JSpinner>()
        value = 4
        awaitIdle()
        assertEquals(4, spinner.value, "the spinner should show the declared value")

        val field = (spinner.editor as JSpinner.DefaultEditor).textField
        field.text = "7"
        field.commitEdit()
        awaitIdle()

        assertEquals(7, spinner.value, "a spinner declared an int again should commit integers again")
        assertEquals(7, value, "and report one")
    }

    @Test
    fun aCommitUnderADeclaredFormatCarriesTheClassDeclaredLast() = runComposeSwingTest {
        // A declared pattern builds the spinner's editor itself, so the class the value is declared in
        // has to reach that editor too.
        var value by mutableStateOf<Number>(1)
        setContent { Spinner(value = value, onValueChange = { value = it }, format = "#.##") }

        val spinner = onNodeOfType<JSpinner>().fetch<JSpinner>()
        value = 2.5
        awaitIdle()

        val field = (spinner.editor as JSpinner.DefaultEditor).textField
        field.text = field.formatter.valueToString(3.5)
        field.commitEdit()
        awaitIdle()

        assertEquals(3.5, spinner.value, "a commit should keep the decimals the pattern renders")
        assertEquals(3.5, value, "and be reported as what was typed")
    }
}
