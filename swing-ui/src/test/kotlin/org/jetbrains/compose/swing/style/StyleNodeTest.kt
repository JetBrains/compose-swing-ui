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
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.modifier.resetModifierState
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class StyleNodeTest {
    @Test
    fun theKindlessStyleOffersJComponentPropertiesAndProvidesNestedStyles() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier =
                    SwingModifier.testTag("container").styleable {
                        toolTip("container")
                        provideNestedStyle(Probes) { LabelToolTip.provide("nested") }
                    },
            ) {
                SwingNode(factory = { JLabel() }, modifier = SwingModifier.styleable(SwingStyle, NestedProbe))
            }
        }
        onNodeWithTag("container").assertIsOfType<JPanel>().assertProperty("container") { toolTipText }
        onNodeOfType<JLabel>().assertProperty("nested") { toolTipText }
    }

    @Test
    fun theKindlessStyleRejectsANonJComponentBeforeWritingProperties() = runComposeSwingTest {
        val target = object : Component() {}.apply { background = Color.BLUE }
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    SwingNode(factory = { target }, modifier = SwingModifier.styleable { background(Color.RED) })
                }
            }
        assertEquals(Color.BLUE, target.background)
        assertContains(failure.message.orEmpty(), "needs a javax.swing.JComponent")
        assertContains(failure.message.orEmpty(), target.javaClass.name)
        assertContains(failure.message.orEmpty(), "use the styleable overload taking a SwingStyleKind")
    }

    @Test
    fun aRejectedKindlessStyleProvidesNothingToAnExistingChild() = runComposeSwingTest {
        val owner = TestCompositionOwner()
        val target = object : Component() {}.apply { background = Color.BLUE }
        val parent = CreatedNodeHolder(target).attachedTo(owner)
        val child = CreatedNodeHolder(JLabel()).attachedTo(owner)
        val resolutions = ArrayList<Unit>()
        child.parentTo(parent)
        parent.children += child
        try {
            child.applyDeclaredModifier(
                SwingModifier.styleable({
                    resolutions += Unit
                }, NestedProbe),
            )
            val before = resolutions.toList()
            val childTip = child.component.toolTipText
            val failure =
                assertFailsWith<RuntimeException> {
                    parent.applyDeclaredModifier(
                        SwingModifier.styleable {
                            background(Color.RED)
                            provideNestedStyle(Probes) { LabelToolTip.provide("rejected") }
                        },
                    )
                }
            assertEquals(Color.BLUE, target.background)
            assertEquals(childTip, child.component.toolTipText, "the child receives no rejected nested style")
            assertEquals(before, resolutions, "a rejected provider must not provide or restyle its child")
            assertIs<IllegalArgumentException>(failure)
        } finally {
            child.resetModifierState()
            parent.resetModifierState()
            owner.dispose()
        }
    }

    @Test
    fun anExplicitKindStylesANonJComponent() = runComposeSwingTest {
        var target: Component? = null
        setContent {
            SwingNode(
                factory = { object : Component() {}.also { target = it } },
                modifier = SwingModifier.styleable({ background(Color.RED) }, PlainProbe),
            )
        }
        assertEquals(Color.RED, target?.background)
    }

    @Test
    fun switchingANonJComponentFromAKindToTheKindlessStyleFails() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    val target = object : Component() {}
                    var custom by mutableStateOf(true)
                    setContent {
                        SwingNode(
                            factory = { target },
                            modifier =
                                if (custom) {
                                    SwingModifier.styleable({ background(Color.RED) }, PlainProbe)
                                } else {
                                    SwingModifier.styleable { background(Color.BLUE) }
                                },
                        )
                    }
                    custom = false
                    awaitIdle()
                }
            }
        assertContains(failure.message.orEmpty(), "needs a javax.swing.JComponent")
    }

    @Test
    fun aButtonRejectsAStyleableInItsModifier() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    Button("button", onClick = {}, modifier = SwingModifier.styleable { background(Color.RED) })
                }
            }
        assertContains(failure.message.orEmpty(), "style parameter")
        assertContains(failure.message.orEmpty(), "merge them into one style")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
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

    @Test
    fun anUnstyledComponentOfAThirdPartyNestingKindRunsNoDefault() = runComposeSwingTest {
        var defaultRuns = 0
        val kind =
            nestingStyleKind<ProbeScope, Unit>(
                { base, _ -> object : ProbeScope, ComponentStyleScope by base {} },
            ) {
                defaultRuns++
                applyNestedStyle(Probes)
            }
        setContent {
            SwingNode(factory = { StyleProbe() }, modifier = SwingModifier.styleable(SwingStyle, kind))
        }
        assertEquals(0, defaultRuns, "nothing above provides, so the default is not run")
    }
}
