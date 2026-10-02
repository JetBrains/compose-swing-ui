package org.jetbrains.compose.swing.core

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompositionDisposalFailureTest {
    /** A teardown step of a disposal the test can make throw. */
    private enum class Step {
        EFFECT,
        CHILD_REMOVAL,
        LISTENER_REMOVAL,
        PROPERTY_LISTENER,
        OWNER_CLEAR_LISTENER,
        WINDOW_LISTENER_REMOVAL,
    }

    @TestFactory
    fun disposingContentThrowsTheFirstFailingStepAndStillEndsTheContent(): List<DynamicTest> = listOf(
        disposes("no step fails"),
        disposes("an effect's disposal fails", Step.EFFECT),
        // A child removal that throws can leave the effect undisposed, so it is not pinned to once.
        disposes("the host's child removal fails", Step.CHILD_REMOVAL, effectRuns = 0..1),
        disposes("the host's listener removal fails", Step.LISTENER_REMOVAL),
        disposes("the window's listener removal fails", Step.WINDOW_LISTENER_REMOVAL),
        disposes("a listener removal and then an effect's disposal fail", Step.LISTENER_REMOVAL, Step.EFFECT),
        disposes("a listener removal and then a property listener fail", Step.LISTENER_REMOVAL, Step.PROPERTY_LISTENER),
        disposes("an effect's disposal and then an owner-clear listener fail", Step.EFFECT, Step.OWNER_CLEAR_LISTENER),
    )

    /**
     * The steps in [failing] throw, the first of them reaching the caller with the later ones suppressed on it.
     * The effect is disposed [effectRuns] times.
     */
    private fun disposes(
        name: String,
        vararg failing: Step,
        effectRuns: IntRange = 1..1,
    ) = DynamicTest.dynamicTest(name) {
        runSwingTest {
            val failures = failing.associateWith { IllegalStateException("$it failed") }
            val window =
                if (Step.WINDOW_LISTENER_REMOVAL in failures) {
                    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
                    FailingRemovalFrame()
                } else {
                    null
                }
            val panel = FailingRemovalPanel()
            window?.contentPane?.add(panel)
            var armedFailures = emptyMap<Step, Throwable>()
            panel.throwOnClear { armedFailures }

            fun arm(armed: Boolean) {
                armedFailures = if (armed) failures else emptyMap()
                panel.childRemoval = armedFailures[Step.CHILD_REMOVAL]
                panel.listenerRemoval = armedFailures[Step.LISTENER_REMOVAL]
                window?.windowListenerRemoval = armedFailures[Step.WINDOW_LISTENER_REMOVAL]
            }

            fun listeners() = listOf(
                panel.hierarchyListeners.toList(),
                window?.windowListeners?.toList(),
                window?.windowFocusListeners?.toList(),
            )

            val parent = Recomposer(Dispatchers.Swing)
            val before = listeners()
            lateinit var owner: LifecycleOwner
            var disposals = 0
            val content =
                panel.setContent(parent = parent) {
                    owner = LocalLifecycleOwner.current
                    Label("mounted")
                    DisposableEffect(Unit) {
                        onDispose {
                            disposals++
                            failures[Step.EFFECT]?.let { throw it }
                        }
                    }
                }
            try {
                arm(true)
                var thrown: Throwable? = null
                val reported =
                    reportedUncaught {
                        thrown = runCatching { content.dispose() }.exceptionOrNull()
                        content.dispose()
                    }

                val expected = failing.map(failures::getValue)
                assertSame(expected.firstOrNull(), thrown, "the first failure reaches the caller")
                assertEquals(expected.drop(1), thrown?.suppressed?.toList().orEmpty(), "later failures ride on it")
                assertEquals(emptyList(), reported, "a failing step is thrown, and disposing again retries nothing")
                assertTrue(disposals in effectRuns, "the effect is disposed $effectRuns times, got $disposals")
                assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
                assertNull(panel.findLifecycleOwner(), "a destroyed owner is not left published on the host")
                assertEquals(0, panel.componentCount, "the composed child is removed")
                assertEquals(before, listeners(), "the listeners the content added are removed")

                arm(false)
                assertMountsAgain(panel, parent, owner)
            } finally {
                arm(false)
                content.dispose()
                parent.cancel()
                window?.dispose()
            }
        }
    }

    /** The host's raw property listeners throw what [armed] holds as a key the content stands under clears. */
    private fun JPanel.throwOnClear(armed: () -> Map<Step, Throwable>) = addPropertyChangeListener { event ->
        val step =
            when (event.oldValue) {
                is DisposableHandle -> Step.PROPERTY_LISTENER
                is LifecycleOwner -> Step.OWNER_CLEAR_LISTENER
                else -> null
            }
        if (event.newValue == null) armed()[step]?.let { throw it }
    }

    /** The host takes a new content, which is given an owner of its own rather than the ended [ended]. */
    private fun assertMountsAgain(
        panel: JPanel,
        parent: Recomposer,
        ended: LifecycleOwner,
    ) {
        lateinit var replacement: LifecycleOwner
        val content = panel.setContent(parent = parent) { replacement = LocalLifecycleOwner.current }
        try {
            assertNotSame(ended, replacement)
            assertEquals(Lifecycle.State.CREATED, replacement.lifecycle.currentState)
        } finally {
            content.dispose()
        }
    }

    @Test
    fun onDemandDisposalPropagatesAnEffectFailureAndRemainsDisposed() = runSwingTest {
        val parent = Recomposer(Dispatchers.Swing)
        val failure = IllegalStateException("effect disposal failed")
        var disposals = 0
        val content =
            OnDemandComposition(parent) {
                SwingNode(factory = { JLabel("mounted") })
                DisposableEffect(Unit) {
                    onDispose {
                        disposals++
                        throw failure
                    }
                }
            }
        val component = content.recompose {}
        try {
            val reported =
                reportedUncaught {
                    assertSame(failure, assertFailsWith<IllegalStateException> { content.dispose() })
                    content.dispose()
                }
            assertEquals(emptyList(), reported)
            assertEquals(1, disposals)
            assertSame(component, content.recompose { error("disposed content must skip updates") })
        } finally {
            content.dispose()
            parent.cancel()
        }
    }
}
