package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.stylePropertyOf
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Component
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class FoundationStyleableTest {
    @Test
    fun rejectsTwoStyles() =
        runComposeSwingTest {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    setContent {
                        Box(
                            modifier =
                                SwingModifier.styleable { background(Color.RED) }.styleable { background(Color.BLUE) },
                        )
                    }
                }
            assertContains(failure.message.orEmpty(), "only one styleable")
        }

    @ParameterizedTest
    @ValueSource(strings = ["Box", "Empty Box", "Row", "Column", "Layout", "Canvas"])
    fun appliesOwnStyleAndRestoresBackground(component: String) =
        runComposeSwingTest {
            var stage by mutableIntStateOf(0)
            val panelName =
                stylePropertyOf(
                    ComponentPropertyDescriptor<Component, String?>(
                        "name",
                        { it.name },
                        { target, value -> target.name = value },
                    ),
                )
            val style =
                SwingStyle<ComponentStyleScope> {
                    foreground(Color(40, 50, 60))
                    panelName.provide("caller")
                    when (stage) {
                        1 -> background(Color(10, 20, 30))
                        2 -> background(Color(40, 50, 60))
                    }
                }
            setContent {
                Panel(style = { provideNestedStyle(AllComponents) { background(Color(10, 20, 30)) } }) {
                    val modifier = SwingModifier.testTag("target").styleable(style)
                    when (component) {
                        "Box" -> Box(modifier) {}
                        "Empty Box" -> Box(modifier)
                        "Row" -> Row(modifier) {}
                        "Column" -> Column(modifier) {}
                        "Layout" -> Layout(measurePolicy = EmptyBoxMeasurePolicy, modifier = modifier)
                        "Canvas" -> Canvas(modifier) {}
                    }
                }
            }
            val node = onNodeWithTag("target")
            node.assertProperty(Color(40, 50, 60)) { foreground }
            node.assertProperty(false) { background == Color(10, 20, 30) }
            node.assertProperty("caller") { name }
            for ((next, color) in listOf(1 to Color(10, 20, 30), 2 to Color(40, 50, 60))) {
                stage = next
                awaitIdle()
                node.assertProperty(true to color) { isBackgroundSet to background }
            }
            stage = 0
            awaitIdle()
        }

    @Test
    fun labelStylePassesThroughBoxes() =
        runComposeSwingTest {
            setContent {
                Panel(style = { provideNestedStyle(Labels) { foreground(Color.BLUE) } }) {
                    Box {
                        Box(modifier = SwingModifier.styleable(SwingStyle)) {
                            Label("nested", modifier = SwingModifier.testTag("label"))
                        }
                    }
                }
            }
            onNodeWithTag("label").assertProperty(Color.BLUE) { foreground }
        }
}
