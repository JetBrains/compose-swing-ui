package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.ComponentOrientation
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Flipping a [BorderLayout]-backed container between left-to-right and right-to-left through
 * [componentOrientation] must request a relayout, or its orientation-aware `LINE_START`/`LINE_END`
 * children keep the edges they were last laid out on and the change is invisible.
 *
 * The change is driven through the public API ([SwingNode] plus the modifier, re-applied across the
 * recomposition the orientation state provokes), and two things are observed off-screen: that the
 * container's `revalidate()` was called - counted by a [JPanel] subclass, since Swing's own relayout
 * plumbing is inert without a peer - and that the children then swapped horizontal edges.
 */
class LayoutOrientationTest {
    /** A [BorderLayout] panel that counts how many times a relayout was requested on it. */
    private class RevalidateCountingPanel(
        private val revalidateCount: AtomicInteger?,
    ) : JPanel(BorderLayout()) {
        override fun revalidate() {
            // Guard against super-constructor calls that run before the constructor parameter binds
            // (it is null on the JVM until super() returns).
            revalidateCount?.incrementAndGet()
            super.revalidate()
        }
    }

    @Test
    fun togglingComponentOrientationRequestsRelayoutAndSwapsLineStartLineEndEdges() = runComposeSwingTest {
        var rtl by mutableStateOf(false)
        val revalidateCount = AtomicInteger(0)
        val leading = JLabel("leading")
        val trailing = JLabel("trailing")

        setContent {
            SwingNode(
                factory = {
                    RevalidateCountingPanel(revalidateCount).also {
                        it.add(leading, BorderLayout.LINE_START)
                        it.add(trailing, BorderLayout.LINE_END)
                    }
                },
                modifier =
                    SwingModifier.componentOrientation(
                        if (rtl) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT,
                    ),
            )
        }

        val leadingLtr = leading.x
        val trailingLtr = trailing.x
        assertTrue(
            leadingLtr < trailingLtr,
            "Under LTR, LINE_START (x=$leadingLtr) should sit left of LINE_END (x=$trailingLtr).",
        )

        // Flip orientation through state; the modifier element is re-applied with the new value.
        val countBeforeToggle = revalidateCount.get()
        rtl = true
        awaitIdle()
        val countAfterToggle = revalidateCount.get()

        assertTrue(
            countAfterToggle > countBeforeToggle,
            "Toggling componentOrientation must request a relayout (revalidate). " +
                "Count before=$countBeforeToggle after=$countAfterToggle - the container was " +
                "never invalidated, so a reactive RTL change would not re-lay-out.",
        )

        val leadingRtl = leading.x
        val trailingRtl = trailing.x
        assertTrue(
            leadingRtl > trailingRtl,
            "After toggling to RTL, LINE_START (x=$leadingRtl) should sit right of LINE_END " +
                "(x=$trailingRtl).",
        )
    }
}
