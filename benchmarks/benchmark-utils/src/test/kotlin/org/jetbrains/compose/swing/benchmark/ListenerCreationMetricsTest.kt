// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.testutils.ComposeTestCase
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import kotlin.test.assertEquals

/** Tests that a toggle that replaces a listener counts a listener creation. */
class ListenerCreationMetricsTest {
    @TestFactory
    fun `replacing a listener counts its creation`(): List<DynamicTest> =
        listOf(
            "ordinary listeners" to ListenerReplacementCase(),
            "equal distinct listeners" to ListenerReplacementCase(before = EqualListener(), after = EqualListener()),
        ).map { (name, subject) ->
            DynamicTest.dynamicTest(name) {
                runSwingTest {
                    SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                        control.compose()
                        control.settle()
                        val metrics = control.measure { subject.toggle() }
                        assertEquals(1L, metrics.getValue("listenersCreated"))
                        assertEquals(0L, control.measure {}.getValue("listenersCreated"))
                    }
                }
            }
        }

    private data class EqualListener(
        val tag: Int = 0,
    ) : ActionListener {
        override fun actionPerformed(event: ActionEvent) = Unit
    }

    private class ListenerReplacementCase(
        private val before: ActionListener = ActionListener { },
        private val after: ActionListener = ActionListener { },
    ) : ComposeTestCase {
        private var changed by mutableStateOf(false)

        fun toggle() {
            changed = !changed
        }

        @Composable
        override fun Content() {
            val replaced = changed
            SwingNode(
                factory = { JButton("Button") },
                update = {
                    set(replaced) { isReplaced ->
                        actionListeners.forEach { removeActionListener(it) }
                        addActionListener(if (isReplaced) after else before)
                    }
                },
            )
        }
    }
}
