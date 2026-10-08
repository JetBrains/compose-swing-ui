package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.NaturalSize.CONTAINER
import org.jetbrains.compose.swing.animation.Stretch.CONTENT
import org.jetbrains.compose.swing.components.layout.BorderPanelScope
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.cyclesUntilStable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.assertEquals

/** The sizes, timings and scaffolding that the tests of an animated container in a stock parent's region share. */
internal object NaturalSize {
    /** The region a [ParentRegion] holds its content in. */
    val PARENT: Dimension get() = Dimension(80, 60)

    /** A size larger than [PARENT] on both axes. */
    val OWN: Dimension get() = Dimension(200, 120)

    const val CONTAINER = "container"

    /** A tween duration or delay that holds an animation near its start through the frames a test drives. */
    const val LONG = 640

    /** A flow row holding a border panel of [size], whose center is where the animated container sits. */
    @Composable
    fun ParentRegion(
        size: Dimension,
        modifier: SwingModifier = SwingModifier,
        content: @Composable BorderPanelScope.() -> Unit,
    ) {
        Panel(PanelLayout.Flow(), modifier) {
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(size), content = content)
        }
    }
}

/**
 * Asserts that the container and its content are both [assigned], the region its stock parent gives the container,
 * whatever size either one prefers; [label] says when.
 */
internal fun ComposeSwingTest.assertWithinAssignedBounds(
    assigned: Dimension,
    label: String,
) {
    onNodeWithTag(CONTAINER)
        .assertProperty(assigned, "$label: the stock parent assigns the container this region") { size }
    onNodeWithTag(CONTENT)
        .assertProperty(assigned, "$label: the content is measured within the assigned bounds") { size }
}

/**
 * Lets the transition run to its end, then asserts that the container rests at [assigned], its content too if
 * [contentRests], and that no further layout pass changes either.
 */
internal suspend fun ComposeSwingTest.assertSettledAt(
    assigned: Dimension,
    contentRests: Boolean = true,
) {
    mainClock.autoAdvance = true
    awaitIdle()
    if (contentRests) {
        onNodeWithTag(CONTENT).assertProperty(assigned, "the content rests in the assigned bounds") { size }
    }
    onNodeWithTag(CONTAINER).assertProperty(assigned, "the container rests in the assigned bounds") { size }
    assertEquals(
        0,
        cyclesUntilStable(onNodeWithTag(CONTAINER).fetch<JComponent>()),
        "no further pass changes the layout",
    )
}
