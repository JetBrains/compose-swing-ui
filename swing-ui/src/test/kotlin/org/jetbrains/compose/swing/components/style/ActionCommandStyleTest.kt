// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionCommandStyleTest {
    @Test
    fun commandsIdentifyButtonsUpdateAndRestoreText() = runComposeSwingTest {
        var command by mutableStateOf<String?>("save")
        val fired = mutableListOf<String>()
        setContent {
            val listener = remember { ActionListener { fired += it.actionCommand } }
            Button("Save", actionListener = listener, style = { command?.let { actionCommand(it) } })
            Button("Discard", actionListener = listener, style = { actionCommand("discard") })
        }
        onNodeWithText("Discard").performClick()
        onNodeWithText("Save").performClick()
        assertEquals(listOf("discard", "save"), fired)
        fired.clear()

        onNodeWithText("Save").performClick()
        command = "save-as"
        awaitIdle()
        onNodeWithText("Save").performClick()
        assertEquals(listOf("save", "save-as"), fired)
        fired.clear()

        command = "save"
        awaitIdle()
        onNodeWithText("Save").performClick()
        command = null
        awaitIdle()
        onNodeWithText("Save").performClick()
        assertEquals(listOf("save", "Save"), fired)
    }
}
