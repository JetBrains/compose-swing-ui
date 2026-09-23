package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** How a child's visibility carries through a policy leaving it unplaced and placing it again. */
class UnplacedChildVisibilityTest {
    /** A child left unplaced stays hidden when the component defaults it inherits change. */
    @Test
    fun anUnplacedChildStaysHiddenWhenItsComponentDefaultsChange() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            var orientation by mutableStateOf(ComponentOrientation.LEFT_TO_RIGHT)
            setContent {
                ProvideComponentDefaults(DefaultComponentOrientation provides orientation) {
                    Layout(
                        content = { SizedChild(0, SwingModifier.testTag("child")) },
                        measurePolicy = { measurables, constraints ->
                            val placeable = measurables.single().measure(constraints)
                            layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                        },
                    )
                }
            }
            val child = onNodeWithTag("child").fetch<Component>()

            placed = false
            awaitIdle()
            val reported = child.visibilityChanges()
            orientation = ComponentOrientation.RIGHT_TO_LEFT
            awaitIdle()

            assertEquals(ComponentOrientation.RIGHT_TO_LEFT, child.componentOrientation, "the defaults must apply")
            assertEquals(emptyList(), reported, "the unplaced child must stay hidden")

            placed = true
            awaitIdle()

            assertTrue(child.isVisible, "the child must be shown once placed again")
        }

    /** A child app code hid stays hidden once the policy places it again. */
    @Test
    fun aChildHiddenOutsideTheModifierStaysHiddenWhenPlacedAgain() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.testTag("child")) },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                    },
                )
            }
            val child = onNodeWithTag("child").fetch<Component>()
            child.isVisible = false

            placed = false
            awaitIdle()
            placed = true
            awaitIdle()

            assertFalse(child.isVisible, "the child must stay hidden")
        }

    /**
     * A child declaring `visible(false)` is neither shown nor hidden while the policy leaves it unplaced, places it
     * again, or it leaves the container.
     */
    @Test
    fun aHiddenChildReportsNoVisibilityChangeAcrossPlacement() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            var inLayout by mutableStateOf(true)
            setContent {
                val child =
                    remember {
                        movableContentOf { SizedChild(0, SwingModifier.testTag("child").visible(false)) }
                    }
                if (inLayout) {
                    Layout(
                        content = { child() },
                        measurePolicy = { measurables, constraints ->
                            val placeable = measurables.single().measure(constraints)
                            layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                        },
                    )
                } else {
                    Panel(PanelLayout.Flow()) { child() }
                }
            }
            val child = onNodeWithTag("child").fetch<Component>()
            val reported = child.visibilityChanges()

            placed = false
            awaitIdle()
            placed = true
            awaitIdle()
            assertEquals(emptyList(), reported, "placing the hidden child again must not show it")

            placed = false
            awaitIdle()
            inLayout = false
            awaitIdle()
            assertEquals(emptyList(), reported, "the hidden child leaving the container unplaced must not show it")
            assertSame(child, onNodeWithTag("child").fetch<Component>(), "the move must keep the component")
            assertFalse(child.isVisible, "the child must stay hidden")
        }
}

/** Records each time this component is shown or hidden from now on, in order. */
private fun Component.visibilityChanges(): List<String> {
    val reported = mutableListOf<String>()
    addComponentListener(
        object : ComponentAdapter() {
            override fun componentShown(event: ComponentEvent) {
                reported += "shown"
            }

            override fun componentHidden(event: ComponentEvent) {
                reported += "hidden"
            }
        },
    )
    return reported
}
