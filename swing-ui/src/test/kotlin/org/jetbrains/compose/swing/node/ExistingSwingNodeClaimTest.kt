package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Color
import java.awt.Component
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What a claim finds and which declaration names it: its types, its declaring call and its uniqueness. */
class ExistingSwingNodeClaimTest {
    @Test
    fun aClaimComposedUnderAnotherTypeOfParentIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Panel { ExistingSwingNode(claim = PartsPanel::part) }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("claims its component from a PartsPanel, but it is composed under a JPanel"),
            "the refusal should name the parent type the claim needs and the one it found: $message",
        )
    }

    @Test
    fun aClaimThatFindsNothingIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    SwingNode(factory = { PartsPanel() }) {
                        ExistingSwingNode<PartsPanel, _, JLabel>(claim = { null })
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("found no component: its claim returned null on the PartsPanel"),
            "the refusal should say the claim found nothing on its parent: $message",
        )
    }

    @Test
    fun aClaimThroughAGetterTypedComponentThatFindsAnotherTypeIsRefused() = runComposeSwingTest {
        assertFailsWith<IllegalStateException> {
            setContent {
                SwingNode(factory = { PartsPanel() }) {
                    ExistingSwingNode<PartsPanel, _, JButton>(claim = PartsPanel::wide)
                }
            }
            awaitIdle()
        }
    }

    @Test
    fun aClaimThroughAGetterTypedComponentConfiguresThePartOfTheTypeItNames() = runComposeSwingTest {
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ExistingSwingNode<PartsPanel, _, JLabel>(
                    claim = PartsPanel::wide,
                    update = { set("declared") { text = it } },
                )
            }
        }

        onNodeOfType<PartsPanel>().assertProperty("declared") { part.text }
    }

    @Test
    fun aContainerClaimTakesItsContentAsATrailingLambda() = runComposeSwingTest {
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ExistingSwingNode(claim = PartsPanel::pane) { Label("child") }
            }
        }

        onNodeOfType<PartsPanel>().assertProperty(1) { pane.componentCount }
    }

    @Test
    fun aContainerClaimThroughAWideGetterComposesContentIntoTheNamedType() = runComposeSwingTest {
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ExistingSwingNode<PartsPanel, Component, JPanel>(
                    claim = PartsPanel::widePane,
                    update = { set("declared") { name = it } },
                ) { Label("child") }
            }
        }

        onNodeOfType<PartsPanel>().assertTreeMatches(
            PartsPanel().apply {
                pane.name = "declared"
                pane.add(JLabel("child"))
            },
        )
    }

    @TestFactory
    fun aSecondClaimUnderAnIndexedHostIsRefusedNamingItsDeclaringCall(): List<DynamicTest> = listOf(
        TwoClaims("the same call names it", "Part()") {
            ExistingSwingNode(claim = PartsPanel::part, declaringCall = "Part()")
            ExistingSwingNode(claim = PartsPanel::part, declaringCall = "Part()")
        },
        TwoClaims("different calls name the type", null) {
            ExistingSwingNode(claim = PartsPanel::part, declaringCall = "First()")
            ExistingSwingNode(claim = PartsPanel::part, declaringCall = "Second()")
        },
        TwoClaims("the same call over a container names it", "Pane { }") {
            ExistingSwingNode(claim = PartsPanel::pane, declaringCall = "Pane { }") { Label("first") }
            ExistingSwingNode(claim = PartsPanel::pane, declaringCall = "Pane { }") { Label("second") }
        },
        TwoClaims("a named claim then an unnamed one name the type", null) {
            ExistingSwingNode(claim = PartsPanel::part, declaringCall = "Part()")
            ExistingSwingNode(claim = PartsPanel::part)
        },
        TwoClaims("an unnamed claim then a named one name the type", null) {
            ExistingSwingNode(claim = PartsPanel::part)
            ExistingSwingNode(claim = PartsPanel::part, declaringCall = "Part()")
        },
    ).map { case ->
        dynamicTest(case.name) {
            runComposeSwingTest {
                val message =
                    assertFailsWith<IllegalStateException> {
                        setContent { SwingNode(factory = { PartsPanel() }) { case.claims() } }
                        awaitIdle()
                    }.message.orEmpty()

                case.declaringCall?.let { assertTrue(message.contains(it), message) }
            }
        }
    }

    @TestFactory
    fun aDeclaringCallChangedWhileClaimedNamesTheNextRefusal(): List<DynamicTest> = listOf(
        ChangedCall("Changed()"),
        ChangedCall(null),
    ).map { case ->
        dynamicTest("changed to ${case.changed}") {
            runComposeSwingTest {
                var call by mutableStateOf<String?>("Part()")
                var generation by mutableIntStateOf(0)
                setContent {
                    SwingNode(factory = { PartsPanel() }) {
                        key(generation) { ExistingSwingNode(claim = PartsPanel::part, declaringCall = call) }
                    }
                }
                call = case.changed
                awaitIdle()

                // The node under the new key is inserted before the one it replaces is removed.
                val message =
                    assertFailsWith<IllegalStateException> {
                        generation = 1
                        awaitIdle()
                    }.message.orEmpty()

                case.changed?.let { assertTrue(message.contains(it), message) }
            }
        }
    }

    @Test
    fun aChangedClaimIsNotCalledAndTheNodeKeepsItsComponent() = runComposeSwingTest {
        val later: PartsPanel.() -> JLabel = { throw LaterClaimCalled() }
        var changed by mutableStateOf(false)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ExistingSwingNode(
                    claim = if (changed) later else PartsPanel::part,
                    modifier = SwingModifier.styleable { background(if (changed) Color.BLUE else Color.RED) },
                )
            }
        }

        changed = true
        awaitIdle()

        val panel = onNodeOfType<PartsPanel>()
        panel.assertProperty(Color.BLUE, "the node should go on configuring the component it claimed") {
            part.background
        }
        panel.assertProperty(
            PartsPanel().other.background,
            "the component the changed claim names should be left alone",
        ) { other.background }
    }

    @Test
    fun aSecondNodeClaimingTheSameComponentIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    SwingNode(factory = { PartsPanel() }) {
                        ExistingSwingNode(claim = PartsPanel::part)
                        ExistingSwingNode(claim = PartsPanel::part)
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("A JLabel is declared twice at once"),
            "the refusal should say the component is claimed twice: $message",
        )
    }

    @Test
    fun aNodeReplacingTheClaimUnderAChangedKeyIsRefused() = runComposeSwingTest {
        // The replacing node arrives before the node it replaces is released, so both would configure the
        // component. An on-demand composition reports a failure of its pass to the caller that drove it.
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        var generation by mutableIntStateOf(0)
        val composition =
            OnDemandComposition(parentContext) {
                SwingNode(factory = { PartsPanel() }) {
                    key(generation) { ExistingSwingNode(claim = PartsPanel::part) }
                }
            }
        try {
            val message = failureOf { composition.recompose { generation = 1 } }

            assertTrue(
                message.contains("A JLabel is declared twice at once"),
                "the refusal should say the component is claimed twice: $message",
            )
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aClaimRelocatedByMovableContentIsNotASecondClaim() = runComposeSwingTest {
        var first by mutableStateOf(true)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                val claim =
                    remember {
                        movableContentOf {
                            ExistingSwingNode(
                                claim = PartsPanel::part,
                                modifier = SwingModifier.styleable { background(Color.RED) },
                            )
                        }
                    }
                if (first) claim()
                Label("between")
                if (!first) claim()
            }
        }

        first = false
        awaitIdle()

        val panel = onNodeOfType<PartsPanel>()
        panel.assertProperty(true, "the relocated claim should leave the component where its parent has it") {
            part.parent === this
        }
        panel.assertProperty(Color.RED, "the relocated claim should go on configuring the component") {
            part.background
        }
    }

    @Test
    fun keyingOnAReplacedPartMovesTheDeclarationsToIt() = runComposeSwingTest {
        val panel = PartsPanel()
        val replaced = panel.part
        val original = replaced.background
        var current by mutableStateOf<JLabel?>(null)
        setContent {
            SwingNode(factory = { panel }) {
                key(current) {
                    ExistingSwingNode(
                        claim = PartsPanel::part,
                        modifier = SwingModifier.styleable { background(Color.RED) },
                    )
                }
            }
        }

        current = panel.replacePart()
        awaitIdle()

        onNodeOfType<PartsPanel>().assertProperty(Color.RED, "the new part should carry the declarations") {
            part.background
        }
        assertEquals(original, replaced.background, "the replaced part should get its background back")
    }
}

/** Two claims of one part of a [PartsPanel]; the refusal they raise names [declaringCall] when one is given. */
private class TwoClaims(
    val name: String,
    val declaringCall: String?,
    val claims: @Composable () -> Unit,
)

/** A declaring call changed to [changed] while claimed; the next refusal names it when it is not `null`. */
private class ChangedCall(
    val changed: String?,
)

/** Thrown by a claim a test supplies after the node is created, to fail the test if the claim is ever called. */
private class LaterClaimCalled : RuntimeException()
