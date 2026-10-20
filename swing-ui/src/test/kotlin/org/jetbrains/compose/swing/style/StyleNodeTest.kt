package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A style declared through `styleable` on a custom component, and what it keeps observing as it changes. */
class StyleNodeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("secondStyleables")
    fun aSecondStyleableOnOneComponentIsRefused(addedByRecomposition: Boolean) {
        assertFailsWith<IllegalArgumentException> {
            runComposeSwingTest {
                var second by mutableStateOf(!addedByRecomposition)
                setContent {
                    SwingNode(
                        factory = { JLabel() },
                        modifier =
                            SwingModifier
                                .testTag("target")
                                .styleable({ LabelToolTip.provide("first") }, PlainProbe)
                                .then(
                                    if (second) {
                                        SwingModifier.styleable({ LabelToolTip.provide("second") }, PlainProbe)
                                    } else {
                                        SwingModifier
                                    },
                                ),
                    )
                }
                if (addedByRecomposition) {
                    onNodeWithTag("target").assertIsOfType<JLabel>().assertProperty("first") { toolTipText }
                    second = true
                    awaitIdle()
                }
            }
        }
    }

    @Test
    fun aChangingStyleAndARepeatedOtherKeyRemainValidAcrossRecompositions() = runComposeSwingTest {
        var tip by mutableStateOf("first")
        setContent {
            val current = tip
            SwingNode(
                factory = { JLabel() },
                modifier =
                    SwingModifier
                        .testTag("first")
                        .testTag("last")
                        .styleable({ LabelToolTip.provide(current) }, PlainProbe),
            )
        }
        onNodeWithTag("last").assertIsOfType<JLabel>().assertProperty("first") { toolTipText }
        tip = "second"
        awaitIdle()
        onNodeWithTag("last").assertIsOfType<JLabel>().assertProperty("second") { toolTipText }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("observingDeclarations")
    fun aNodeObservingStateStopsWhenItsStyleStopsSettingIt(inParentLayout: Boolean) = runComposeSwingTest {
        var present by mutableStateOf(true)
        val watched = mutableIntStateOf(0)
        var changes = 0
        var detaches = 0
        val observing =
            stylePropertyOf<Unit> {
                if (inParentLayout) {
                    ObservingLayoutElement(watched::intValue, { detaches++ }) { changes++ }
                } else {
                    ObservingElement(watched::intValue, { detaches++ }) { changes++ }
                }
            }
        setContent {
            SwingNode(factory = { JPanel(RecordingMeasurementLayout(FlowLayout())) }) {
                SwingNode(
                    factory = { JLabel() },
                    modifier = SwingModifier.styleable({ if (present) observing.provide(Unit) }, PlainProbe),
                )
            }
        }
        watched.intValue = 1
        awaitIdle()
        assertEquals(1, changes, "the node hears a change while the style sets it")

        present = false
        awaitIdle()
        assertEquals(1, detaches, "the node leaves with the value")

        watched.intValue = 2
        awaitIdle()
        assertEquals(1, changes, "a node that left hears no change")

        present = true
        awaitIdle()
        watched.intValue = 3
        awaitIdle()
        assertEquals(2, changes, "a value set again observes again")
    }

    @Test
    fun aStyledComponentMovedToAnotherCompositionKeepsObservingThroughBothKindsOfDeclaration() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        val watched = mutableIntStateOf(0)
        var propertyObserved = -1
        var layoutObserved = -1
        val property =
            stylePropertyOf<Unit> { ObservingElement({ watched.intValue.also { propertyObserved = it } }, {}, {}) }
        val layoutProperty =
            stylePropertyOf<Unit> { ObservingLayoutElement({ watched.intValue.also { layoutObserved = it } }, {}, {}) }
        val modifier =
            SwingModifier.styleable({
                property.provide(Unit)
                layoutProperty.provide(Unit)
            }, PlainProbe)
        val host = JPanel(RecordingMeasurementLayout(FlowLayout()))
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        SwingNode(factory = { JLabel("moved") }, modifier = modifier)
                    }
                }
            SwingNode(factory = { host })
            SwingNode(factory = { JPanel(RecordingMeasurementLayout(FlowLayout())) }) {
                if (inOuter) content()
            }
        }
        val handle = host.setContent(parent = context) { if (!inOuter) content() }
        awaitIdle()
        inOuter = true
        awaitIdle()
        handle.dispose()
        watched.intValue++
        awaitIdle()

        assertEquals(listOf(1, 1), listOf(propertyObserved, layoutObserved), "both keep observing in their new place")
    }

    companion object {
        @JvmStatic
        fun secondStyleables(): List<Arguments> = listOf(
            Arguments.of(Named.of("declared together", false)),
            Arguments.of(Named.of("added by a recomposition", true)),
        )

        @JvmStatic
        fun observingDeclarations(): List<Arguments> = listOf(
            Arguments.of(Named.of("a property", false)),
            Arguments.of(Named.of("a parent-layout declaration", true)),
        )
    }
}
