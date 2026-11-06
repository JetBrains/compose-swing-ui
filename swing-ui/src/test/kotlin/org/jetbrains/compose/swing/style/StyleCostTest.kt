package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Buttons
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleCostTest {
    @Test
    fun emptyContainerAddsNoWrites() = runComposeSwingTest {
        val writes = WriteCounter()
        setContent {
            Label("bare", style = { writes.counted.provide("own") })
            Panel(style = {}) {
                Label("under an empty style", style = { writes.counted.provide("own") })
            }
        }
        assertEquals(2, writes.count, "each label writes its own property once, with or without the container")
        onNodeWithText("bare").assertIsOfType<JComponent>().assertProperty("own") { toolTipText }
        onNodeWithText("under an empty style").assertIsOfType<JComponent>().assertProperty("own") { toolTipText }
    }

    @Test
    fun styleAndModifierChangeWritesOnce() = runComposeSwingTest {
        var dark by mutableStateOf(false)
        val writes = WriteCounter()
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(if (dark) Color.BLACK else Color.WHITE) } }) {
                Label(
                    "label",
                    modifier = SwingModifier.property(writes.descriptor, if (dark) "dark" else "light"),
                )
            }
        }
        assertEquals(1, writes.count)
        onNodeWithText("label").assertIsOfType<JComponent>().assertProperty("light") { toolTipText }

        dark = true
        awaitIdle()
        assertEquals(2, writes.count, "a pass changing the provided style and the modifier writes the property once")
        onNodeWithText("label").assertIsOfType<JComponent>().assertProperty("dark") { toolTipText }
    }

    @Test
    fun unchangedPassDoesNoWork() = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        val writes = WriteCounter()
        val nestedRuns = Runs()
        val ownRuns = Runs()
        setContent {
            pass.let { }
            Panel(
                style = {
                    background(Color.GRAY)
                    provideNestedStyle(Labels) {
                        nestedRuns.count++
                        foreground(Color.BLUE)
                    }
                },
            ) {
                Label(
                    "label",
                    style = {
                        ownRuns.count++
                        writes.counted.provide("stable")
                    },
                )
            }
        }
        val attached = listOf(nestedRuns.count, ownRuns.count, writes.count)
        assertEquals(listOf(1, 1, 1), attached)

        repeat(2) {
            pass++
            awaitIdle()
        }
        assertEquals(
            attached,
            listOf(nestedRuns.count, ownRuns.count, writes.count),
            "equal styles run no resolve and write nothing",
        )
    }

    @Test
    fun changedBlocksResolveOnlyTheirConsumers() = runComposeSwingTest {
        var labelColor by mutableStateOf(Color.RED)
        var buttonColor by mutableStateOf(Color.GRAY)
        val labelRuns = Runs()
        val buttonRuns = Runs()
        val writes = WriteCounter()
        val overridingWrites = WriteCounter()
        setContent {
            Panel(style = {
                provideNestedStyle(Labels) {
                    labelRuns.count++
                    background(labelColor)
                    foreground(Color.BLACK)
                }
                provideNestedStyle(Buttons) {
                    buttonRuns.count++
                    background(buttonColor)
                }
            }) {
                Label("first", style = { writes.counted.provide("label") })
                Label("second")
                Label("overriding", style = {
                    background(Color.BLUE)
                    overridingWrites.counted.provide("explicit")
                })
                Button("button", onClick = {})
            }
        }
        assertEquals(listOf(3, 1), listOf(labelRuns.count, buttonRuns.count))
        assertEquals(1, overridingWrites.count)

        labelColor = Color.GREEN
        awaitIdle()
        assertEquals(6, labelRuns.count, "each label resolves once")
        assertEquals(1, buttonRuns.count, "the button reads no changed state")
        onNodeWithText("first").assertProperty(Color.GREEN) { background }
        onNodeWithText("second").assertProperty(Color.GREEN) { background }
        onNodeWithText("overriding").assertProperty(Color.BLUE) { background }
        assertEquals(1, overridingWrites.count, "the winning style is not written again")
        val attached = listOf(labelRuns.count, writes.count, overridingWrites.count)

        buttonColor = Color.GREEN
        awaitIdle()
        onNodeWithText("button").assertProperty(Color.GREEN) { background }
        assertEquals(attached, listOf(labelRuns.count, writes.count, overridingWrites.count))
    }
}

/**
 * Counts the runs of a style block. A counter object rather than a local `var`: a style lambda capturing a `var` is
 * not memoized, so each recomposition of its caller would hand the component a new style and resolve it again.
 */
private class Runs {
    var count = 0
}

private class WriteCounter {
    var count = 0

    val descriptor =
        ComponentPropertyDescriptor<JComponent, String?>(
            name = "countedToolTipText",
            read = { it.toolTipText },
            write = { component, value ->
                count++
                component.toolTipText = value
            },
        )

    val counted = stylePropertyOf(descriptor)
}
