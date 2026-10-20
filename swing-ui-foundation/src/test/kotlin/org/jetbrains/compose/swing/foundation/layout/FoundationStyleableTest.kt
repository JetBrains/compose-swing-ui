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

class FoundationStyleableTest {
    @ParameterizedTest
    @ValueSource(strings = ["Box", "Empty Box", "Row", "Column", "Layout", "Canvas"])
    fun receivesAllComponentsAndItsOwnStyleWins(component: String) =
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
                    if (stage == 1) background(Color(40, 50, 60))
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
            onNodeWithTag("target").apply {
                assertProperty(Color(10, 20, 30), "the component receives AllComponents") { background }
                assertProperty(Color(40, 50, 60)) { foreground }
                assertProperty("caller") { name }
            }
            stage = 1
            awaitIdle()
            onNodeWithTag("target").assertProperty(Color(40, 50, 60), "its own style wins") { background }
            stage = 0
            awaitIdle()
            onNodeWithTag("target").assertProperty(Color(10, 20, 30), "the provided value comes back") { background }
        }

    @Test
    fun nestedStylesReachBoxesAndPassThroughThem() =
        runComposeSwingTest {
            setContent {
                Panel(
                    style = {
                        provideNestedStyle(Labels) { foreground(Color.BLUE) }
                        provideNestedStyle(AllComponents) { background(Color.GREEN) }
                    },
                ) {
                    Box(SwingModifier.testTag("plain")) {
                        Box(modifier = SwingModifier.styleable(SwingStyle)) {
                            Label("nested", modifier = SwingModifier.testTag("label"))
                        }
                    }
                }
            }
            onNodeWithTag("label").assertProperty(Color.BLUE) { foreground }
            onNodeWithTag(
                "plain",
            ).assertProperty(Color.GREEN, "a Box that declares no style receives it") { background }
        }
}
