// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JCheckBox
import kotlin.test.Test

class NestedStyleTest {
    @Test
    fun aLateNestedStyleReadsTheLatestCheckedDeclaration() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        var publishing by mutableStateOf(false)
        setContent {
            Panel(style = {
                if (publishing) {
                    provideNestedStyle(CheckBoxes) {
                        background(if (isChecked) Color.RED else Color.BLUE)
                    }
                }
            }) {
                CheckBox("check", checked, onCheckedChange = {})
            }
        }

        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(true) { isSelected }
        publishing = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(Color.RED) { background }
        checked = false
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(Color.BLUE) { background }
        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(Color.RED) { background }
    }
}
