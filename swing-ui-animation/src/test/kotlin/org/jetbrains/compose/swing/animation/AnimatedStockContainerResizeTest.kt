package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Holder
import org.jetbrains.compose.swing.foundation.layout.assertStockContainerLaysTextOutOnceAtANarrowerWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import kotlin.test.Test

/**
 * Stock containers holding wrapping text inside animated containers, which a measure that runs before the text reflows
 * must not lay out more than once; see [assertStockContainerLaysTextOutOnceAtANarrowerWidth].
 */
class AnimatedStockContainerResizeTest {
    @Test
    fun `text in a stock container is laid out once at a narrower width when a measure runs before it reflows`() =
        assertStockContainerLaysTextOutOnceAtANarrowerWidth(
            mapOf<String, Holder>(
                "AnimatedVisibility" to { modifier, content ->
                    Box(modifier = modifier) { AnimatedVisibility(visible = true) { Box { content() } } }
                },
                "AnimatedContent" to { modifier, content ->
                    Box(modifier = modifier) { AnimatedContent(targetState = true) { if (it) Box { content() } } }
                },
                "Box with animateContentSize" to { modifier, content ->
                    Box(modifier = modifier) { Box(modifier = SwingModifier.animateContentSize()) { content() } }
                },
                "Column with animateContentSize" to { modifier, content ->
                    Box(modifier = modifier) { Column(modifier = SwingModifier.animateContentSize()) { content() } }
                },
            ),
        )
}
