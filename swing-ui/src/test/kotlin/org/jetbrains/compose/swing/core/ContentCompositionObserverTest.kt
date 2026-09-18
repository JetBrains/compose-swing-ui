package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContentCompositionObserverTest {
    @Test
    fun aCompositionStartsNoObserverUntilSomethingReadsOne() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val composition =
            SwingContentComposition.nested(parentContext) { owner ->
                SwingApplier(SwingNodeHolder(JPanel()).attachedTo(owner))
            }
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
}
