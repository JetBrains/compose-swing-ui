// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.RecordedRepaints
import org.jetbrains.compose.swing.SwingTestScope
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.core.OnDemandComposition
import org.jetbrains.compose.swing.core.addingTo
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A caller-owned component placed from one root while another root's call for it stands. */
class MovableComponentTakeoverTest {
    @Test
    fun aRootOnAnotherRecomposerTakesTheComponentOver() = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var inFirst by mutableStateOf(true)
        var secondShown by mutableStateOf(false)
        withTwoRoots {
            mountFirst { if (inFirst) movable(SwingModifier.name("first")) }
            mountSecond { if (secondShown) movable(SwingModifier.name("second")) }
            assertSame(firstRoot, field.parent)

            secondShown = true
            second.awaitIdle()
            assertSame(secondRoot, field.parent)
            assertEquals("second", field.name)
            assertEquals(0, firstRoot.componentCount)

            first.awaitIdle()
            assertSame(secondRoot, field.parent)
            assertEquals("second", field.name)

            inFirst = false
            first.awaitIdle()
            assertSame(secondRoot, field.parent, "the first root's call ending took the component from the taker")
            assertEquals("second", field.name)

            secondShown = false
            second.awaitIdle()
            assertNull(field.parent)
            assertEquals("own", field.name)
        }
    }

    @Test
    fun aRelocatedOnDemandRootGivesTheComponentUpToAnotherRoot() = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        lateinit var parentContext: CompositionContext
        var generation by mutableIntStateOf(0)
        withTwoRoots {
            mountFirst { parentContext = rememberCompositionContext() }
            val composition =
                OnDemandComposition(parentContext) { key(generation) { movable(SwingModifier.name("placed")) } }
            composition.recompose { generation = 1 }
            assertEquals("placed", field.name)
            field.nameWrites.clear()

            val other = mountSecond { movable(SwingModifier.name("foreign")) }
            assertEquals(
                listOf("own", "foreign"),
                field.nameWrites,
                "the root gave its writes back before the other took over",
            )

            composition.dispose()
            assertEquals("foreign", field.name, "the root's teardown gave back what it no longer held")
            assertSame(secondRoot, field.parent)

            dispose(other)
            assertEquals("own", field.name)
        }
    }

    @Test
    fun aTakeoverGivesBackTheCornerTheFirstCallFilled() = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        val own = JLabel("own")
        var taken by mutableStateOf(false)
        withTwoRoots {
            mountFirst {
                ScrollPane(
                    factory = { JScrollPane().apply { setCorner(JScrollPane.UPPER_LEFT_CORNER, own) } },
                    modifier = SwingModifier.name("S"),
                ) {
                    movable(SwingModifier.corner(JScrollPane.UPPER_LEFT_CORNER).name("first"))
                }
            }
            mountSecond { if (taken) movable() }
            val pane = assertIs<JScrollPane>(firstRoot.getComponent(0))
            assertSame(field, pane.getCorner(JScrollPane.UPPER_LEFT_CORNER))
            assertEquals("first", field.name)
            field.nameWrites.clear()
            field.moves.clear()

            taken = true
            second.awaitIdle()
            first.awaitIdle()

            assertEquals(
                listOf("own in S"),
                field.nameWrites,
                "the name was given back while the corner still held the component",
            )
            assertEquals(listOf<String?>(null, "second"), field.moves, "one removal from the corner, then the taker")
            assertSame(secondRoot, field.parent, "the takeover took the component out of the corner")
            assertSame(own, pane.getCorner(JScrollPane.UPPER_LEFT_CORNER), "the corner got its own component back")
        }
    }

    @Test
    fun aTakeoverWhoseUninstallThrowsStillRestoresTheModifierAndUninstallsOnce() = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var uninstalls = 0
        val attachment =
            SlotAttachment<Container> { host, component, _ ->
                host.add(component)
                (
                    {
                        uninstalls++
                        host.remove(component)
                        error("uninstall failed")
                    }
                )
            }
        withTwoRoots {
            val firstHandle =
                mountFirst {
                    Framed { movable(SwingModifier.slot(FramedProtocol, BODY_REGION, attachment).name("first")) }
                }
            assertEquals("first", field.name)

            assertFailsWith<IllegalStateException> { mountSecond { movable() } }

            assertEquals("own", field.name, "the takeover restored the modifier although the uninstall threw")
            runCatching { dispose(firstHandle) }
            assertEquals(1, uninstalls, "the uninstall ran once")
        }
    }

    @Test
    fun aTakeoverRefreshesTheHostWhoseRegionGaveTheComponentUp() = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        lateinit var parentContext: CompositionContext
        withTwoRoots {
            mountFirst { parentContext = rememberCompositionContext() }
            val host = JPanel().also { JPanel().add(it) }
            val composition = OnDemandComposition(parentContext, host, addingTo(host)) { movable() }
            try {
                composition.recompose {}
                assertSame(host, field.parent)
                val recorded = RecordedRepaints()

                withRecordedRepaints(recorded) { mountSecond { movable() } }

                assertEquals(0, host.componentCount)
                recorded.assertAskedForLayout(host, "a takeover of the component it held")
            } finally {
                composition.dispose()
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supersededChanges")
    fun aChangeOnTheSupersededCallLeavesTheComponentWithTheTaker(change: SupersededChange) = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        val call = change.build()
        var taken by mutableStateOf(false)
        withTwoRoots {
            mountFirst { call.content(movable) }
            mountSecond { if (taken) movable(SwingModifier.name("second")) }
            taken = true
            second.awaitIdle()
            first.awaitIdle()
            field.nameWrites.clear()
            field.moves.clear()

            call.steps.forEach { step ->
                step()
                second.awaitIdle()
                first.awaitIdle()
            }

            assertSame(secondRoot, field.parent)
            assertEquals("second", field.name)
            assertEquals(emptyList(), field.nameWrites, "the superseded call wrote to the component")
            assertEquals(emptyList(), field.moves, "the superseded call moved the component")
        }
    }

    @Test
    fun aSupersededCallRelocatedIntoTheRootNamingARegionLeavesTheCompositionUndisposed() = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        lateinit var parentContext: CompositionContext
        var relocated by mutableStateOf(false)
        var taken by mutableStateOf(false)
        withTwoRoots {
            mountFirst {
                parentContext = rememberCompositionContext()
                ScrollPane { if (!relocated) movable(SwingModifier.rowHeader()) }
            }
            mountSecond { if (taken) movable() }
            val composition = OnDemandComposition(parentContext) { if (relocated) movable() }
            try {
                taken = true
                second.awaitIdle()
                first.awaitIdle()

                relocated = true
                first.awaitIdle()
                composition.recompose {}

                var ran = false
                composition.recompose { ran = true }
                assertTrue(ran, "the composition was not disposed")
            } finally {
                composition.dispose()
            }
        }
    }

    @Test
    fun aSupersededCallMovedIntoANestedCompositionTellsTheComponentNothing() = runSwingTest {
        val component = ListeningPanel()
        val movable = movableComponentOf(component)
        var nested by mutableStateOf(false)
        var taken by mutableStateOf(false)
        withTwoRoots {
            mountFirst { Panel { if (nested) NestedContentHost { movable() } else movable() } }
            mountSecond { if (taken) movable() }
            taken = true
            second.awaitIdle()
            first.awaitIdle()
            val told = component.events.toList()

            nested = true
            first.awaitIdle()

            assertEquals(told, component.events, "the superseded call told the component about its move")
            assertSame(secondRoot, component.parent)
        }
    }

    /** A case of the first root's call: [build] makes its own state inside the test. */
    class SupersededChange(
        private val label: String,
        val build: () -> SupersededCallChange,
    ) {
        override fun toString() = label
    }

    /** What happens to the first root after another took the component over: [steps] run in order, then it settles. */
    class SupersededCallChange(
        val steps: List<() -> Unit>,
        val content:
            @Composable @SwingComposable
            (MovableComponent) -> Unit,
    )

    companion object {
        @JvmStatic
        fun supersededChanges(): List<SupersededChange> = listOf(
            SupersededChange("its modifier changes") {
                var firstName by mutableStateOf("first")
                SupersededCallChange(listOf({ firstName = "changed" })) { movable ->
                    movable(SwingModifier.name(firstName))
                }
            },
            SupersededChange("its siblings are reordered") {
                var order by mutableStateOf(listOf(0, 1))
                SupersededCallChange(listOf({ order = listOf(1, 0) }, { order = listOf(0, 1) })) { movable ->
                    Panel { order.forEach { id -> key(id) { if (id == 0) movable() else Label("other") } } }
                }
            },
            SupersededChange("it is inserted again under a changed key") {
                var generation by mutableIntStateOf(0)
                SupersededCallChange(listOf({ generation = 1 })) { movable ->
                    Panel { key(generation) { movable() } }
                }
            },
            SupersededChange("it moves where a region is required, then is hidden") {
                var inPane by mutableStateOf(false)
                var shown by mutableStateOf(true)
                SupersededCallChange(listOf({ inPane = true }, { shown = false })) { movable ->
                    if (shown) {
                        if (inPane) ScrollPane { movable() } else Panel { movable() }
                    }
                }
            },
        )
    }
}

/** Two roots on recomposers the test drives apart, the second one named "second". */
private class TwoRoots(
    scope: SwingTestScope,
) {
    val first = TestRecomposer(scope)
    val second = TestRecomposer(scope)
    val firstRoot = JPanel()
    val secondRoot = JPanel().apply { name = "second" }
    private val handles = ArrayList<DisposableHandle>()

    fun mountFirst(
        content:
            @Composable @SwingComposable
            () -> Unit,
    ): DisposableHandle = firstRoot.setContent(first.recomposer, content).also { handles += it }

    fun mountSecond(
        content:
            @Composable @SwingComposable
            () -> Unit,
    ): DisposableHandle = secondRoot.setContent(second.recomposer, content).also { handles += it }

    /** Disposes [handle] now and leaves it out of [close]. */
    fun dispose(handle: DisposableHandle) {
        handles -= handle
        handle.dispose()
    }

    fun close() {
        val failures = handles.mapNotNull { runCatching { it.dispose() }.exceptionOrNull() }
        first.cancel()
        second.cancel()
        failures.firstOrNull()?.let { throw it }
    }
}

private suspend fun SwingTestScope.withTwoRoots(body: suspend TwoRoots.() -> Unit) {
    val roots = TwoRoots(this)
    try {
        roots.body()
    } finally {
        roots.close()
    }
}
