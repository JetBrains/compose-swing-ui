// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.testutils.ComposeTestCase
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JLabel
import javax.swing.JPanel

/** A case that shows one `SwingNode` panel whose [label] text [toggle] flips, next to a running `LaunchedEffect`. */
internal class TreeCase : ComposeTestCase {
    val label = JLabel()

    private var changed by mutableStateOf(false)

    fun toggle() {
        changed = !changed
    }

    @Composable
    override fun Content() {
        val text = if (changed) "After" else "Before"
        LaunchedEffect(Unit) { awaitCancellation() }
        SwingNode<JPanel>(
            factory = { JPanel().apply { add(label) } },
            update = { set(text) { label.text = it } },
        )
    }
}
