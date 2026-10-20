package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentStyleStateTest {
    @ParameterizedTest
    @EnumSource(StateReadCase::class)
    fun onlyReadStateResolvesStyles(case: StateReadCase) = runComposeSwingTest {
        val probe = StyleProbe("probe")
        val checked = mutableStateOf(false)
        var resolves = 0
        var compositions = 0
        val kind = checkableKind { resolves++ }
        val style =
            SwingStyle<CheckableScope> {
                ProbeFirst.provide(
                    if (case == StateReadCase.Unread) {
                        "fixed"
                    } else if (isChecked) {
                        "checked"
                    } else {
                        "unchecked"
                    },
                )
            }
        setContent {
            SideEffect { compositions++ }
            CheckableFrom({ checked.value }, probe, style, kind)
        }
        onProbe("probe").assertProperty(case.initial) { first }
        val composed = compositions
        val resolved = resolves
        probe.writes.clear()
        checked.value = true
        awaitIdle()
        assertEquals(case.writes, probe.writes)
        assertEquals(resolved + case.resolutions, resolves)
        assertEquals(composed, compositions)
        onProbe("probe").assertProperty(case.updated) { first }
    }

    @Test
    fun aNestedBlockReadsTheConsumersStateNotTheProviders() = runComposeSwingTest {
        val unchecked = StyleProbe("unchecked")
        val checked = StyleProbe("checked")
        val firstChecked = mutableStateOf(false)
        val kind = checkableKind { applyNestedStyle(CheckableProbes) }
        val container =
            SwingStyle<CheckableContainerScope> {
                provideNestedStyle(CheckableProbes) {
                    ProbeFirst.provide(if (isChecked) "checked" else "unchecked")
                }
            }
        setContent {
            val providerState = rememberUpdatedState(true)
            SwingNode(
                factory = { StyleProbe() },
                modifier = SwingModifier.styleable(CheckableContainerKind, providerState, container),
            ) {
                CheckableFrom({ firstChecked.value }, unchecked, SwingStyle, kind)
                Checkable(true, checked, SwingStyle, kind)
            }
        }
        onProbe("unchecked").assertProperty("unchecked", "the block reads the consumer's state, not the provider's") {
            first
        }
        onProbe("checked").assertProperty("checked") { first }
        checked.writes.clear()

        firstChecked.value = true
        awaitIdle()
        onProbe("unchecked").assertProperty("checked", "the consumer whose state flipped resolves its block again") {
            first
        }
        assertEquals(emptyList(), checked.writes, "a consumer whose state stayed writes nothing")
    }
}

private interface CheckableScope : ComponentStyleScope {
    val isChecked: Boolean
}

private interface CheckableContainerScope :
    CheckableScope,
    NestedStyleProviderScope

private val CheckableProbes = NestedStyleKey<CheckableScope>("CheckableProbes")

private fun checkableKind(default: SwingStyle<CheckableScope>) = SwingStyleKind<CheckableScope, State<Boolean>>(
    { base, state ->
        object : CheckableScope, ComponentStyleScope by base {
            override val isChecked: Boolean get() = state.value
        }
    },
    default,
)

private val CheckableContainerKind =
    SwingStyleKind<CheckableContainerScope, State<Boolean>>({ base, state ->
        object : CheckableContainerScope, NestedStyleProviderScope by base {
            override val isChecked: Boolean get() = state.value
        }
    })

/** A stateful component: it feeds [checked] to its style scope through [rememberUpdatedState]. */
@Composable
private fun Checkable(
    checked: Boolean,
    probe: StyleProbe,
    style: SwingStyle<CheckableScope>,
    kind: SwingStyleKind<CheckableScope, State<Boolean>>,
) {
    val state = rememberUpdatedState(checked)
    SwingNode(factory = { probe }, modifier = SwingModifier.styleable(kind, state, style))
}

/** [Checkable] reading [checked] in its own scope, so a flip recomposes nothing above it. */
@Composable
private fun CheckableFrom(
    checked: () -> Boolean,
    probe: StyleProbe,
    style: SwingStyle<CheckableScope>,
    kind: SwingStyleKind<CheckableScope, State<Boolean>>,
) {
    Checkable(checked(), probe, style, kind)
}

enum class StateReadCase(
    val initial: String,
    val updated: String,
    val writes: List<String>,
    val resolutions: Int,
) {
    Unread("fixed", "fixed", emptyList(), 0),
    Read("unchecked", "checked", listOf("first=checked"), 1),
}
