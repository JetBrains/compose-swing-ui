package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.Decorator
import org.jetbrains.compose.swing.foundation.graphics.decoration
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Graphics2D
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Makes a component of its own decoratable from outside the library, through public API alone. */
class ExternalDecoratableTest {
    @Test
    fun theLibraryWritesTheDecorationOncePerChangeAndNotForAPassThatChangesNothing() =
        runComposeSwingTest {
            val opaque = Decorator { graphics, width, height, content -> content(graphics, width, height) }
            val translucent =
                object : Decorator {
                    override val isOpaque: Boolean get() = false

                    override fun paint(
                        graphics: Graphics2D,
                        width: Int,
                        height: Int,
                        content: (Graphics2D, Int, Int) -> Unit,
                    ) = content(graphics, width, height)
                }
            var faded by mutableStateOf(false)
            var tip by mutableStateOf("first")
            setContent {
                Box {
                    SwingNode(
                        factory = { CountingCard() },
                        modifier =
                            SwingModifier.testTag("card").toolTip(tip).decoration(if (faded) translucent else opaque),
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<CountingCard>()
            card.writes = 0

            faded = true
            awaitIdle()
            assertEquals(1, card.writes, "one write for the change")

            tip = "second"
            awaitIdle()
            assertEquals(1, card.writes, "no write for a pass that changes no step")
        }

    /** A decoratable component counting the decorations the library writes to it. */
    private class CountingCard :
        JComponent(),
        Decoratable {
        var writes = 0

        override var decoration: Decoration = Decoration.None
            set(value) {
                writes++
                field = value
            }
    }
}
