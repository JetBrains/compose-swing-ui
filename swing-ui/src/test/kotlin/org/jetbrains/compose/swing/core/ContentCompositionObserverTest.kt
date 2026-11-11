// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContentCompositionObserverTest {
    @Test
    fun aCompositionStartsNoObserverUntilSomethingReadsOne() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val host = JPanel()
        val composition =
            SwingContentComposition.nested(parentContext, CreatedNodeHolder(host), host, ::SwingApplier)
        try {
            var shown by mutableStateOf(true)
            composition.setContent { if (shown) Label("plain") }
            composition.recomposeSynchronously { shown = false }
            val snapshotObserver = composition.snapshotObserver
            assertFalse(snapshotObserver.isStarted, "mounting and releasing plain nodes starts no observer")

            val observer = snapshotObserver.observer
            assertTrue(snapshotObserver.isStarted)
            assertSame(observer, snapshotObserver.observer, "the observer is created once")
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aDerivedStateTheFirstObservedBlockReadsStillTracksItsSource() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        var dark by mutableStateOf(false)
        val color = derivedStateOf { if (dark) Color.BLACK else Color.WHITE }

        val root = JPanel()
        val composition = SwingContentComposition.nested(parentContext, CreatedNodeHolder(root), null, ::SwingApplier)
        try {
            composition.setContent { Label("derived", style = { background(color.value) }) }
            val label = root.getComponent(0) as JLabel
            assertEquals(Color.WHITE, label.background)

            dark = true
            awaitIdle()

            assertEquals(Color.BLACK, label.background, "a change to the derived state's source resolves the style")
        } finally {
            composition.dispose()
        }
    }
}
