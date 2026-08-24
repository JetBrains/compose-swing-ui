package org.jetbrains.compose.swing.core

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What `setContent` refuses, and what it must go on accepting.
 *
 * A container that already holds a live island is asked once for the composition its contents nest
 * into, and cannot give two answers. Such a call fails on the call.
 *
 * The case that refuses is paired with the nearest legal call, because a check that fires on a legal
 * transition is worse than the misuse it guards against.
 */
class SetContentMisuseTest {
    @Test
    fun aContainerAlreadyHoldingALiveIslandRefusesMoreContent() = runSwingTest {
        val panel = JPanel()
        val runtime = SwingRecomposer.create(panel)
        try {
            panel.setContent(parent = runtime.compositionContext) { Label(text = "first") }

            val refused =
                assertFailsWith<IllegalStateException> {
                    panel.setContent(parent = runtime.compositionContext) { Label(text = "second") }
                }
            assertTrue(
                "two islands cannot both be" in refused.message.orEmpty(),
                "the refusal must say why a container answers for one island only, and said: ${refused.message}",
            )
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun aContainerWhoseIslandWasDisposedTakesContentAgain() = runSwingTest {
        val panel = JPanel()
        val runtime = SwingRecomposer.create(panel)
        try {
            val first = panel.setContent(parent = runtime.compositionContext) { Label(text = "first") }
            first.dispose()

            // Only a live island stands in the way, so the container answers for this one now.
            panel.setContent(parent = runtime.compositionContext) { Label(text = "second") }

            assertEquals(1, panel.componentCount, "the container should hold the content mounted second")
        } finally {
            runtime.dispose()
        }
    }
}
