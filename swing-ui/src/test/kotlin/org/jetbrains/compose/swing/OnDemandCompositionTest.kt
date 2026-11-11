// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import java.awt.Container
import java.util.concurrent.Executors
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

/**
 * The contract of [OnDemandComposition], placed or not: it hands back the one top-level component its content
 * composes, terminally refuses more than one, returns from a reentrant call without recomposing again, and
 * returns `null` once disposed.
 */
class OnDemandCompositionTest {
    @Test
    fun recomposeAnswersTheCurrentComponentAfterAStateWriteChangesIt() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var text by mutableStateOf("first")
        val composition = OnDemandComposition(parentContext) { Label(text) }
        try {
            val first = composition.recompose {}
            assertEquals("first", (first as JLabel).text, "the initial pass composes the initial state")

            val second = composition.recompose { text = "second" }
            assertSame(first, second, "the same component is reused across recomposes")
            assertEquals("second", (second as JLabel).text, "the write inside recompose must have applied")
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aRecomposeThatReplacesTheOnlyTopLevelComponentReturnsTheReplacement() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var showButton by mutableStateOf(false)
        val composition =
            OnDemandComposition(parentContext) {
                if (showButton) {
                    Button("second", onClick = {})
                } else {
                    Label("first")
                }
            }
        try {
            assertIs<JLabel>(composition.recompose {}, "the initial branch composes its label")

            val replacement = assertIs<JButton>(composition.recompose { showButton = true })
            assertEquals("second", replacement.text, "the replacement branch composes its button")
            assertSame(
                replacement,
                composition.recompose {},
                "the replacement is the component the composition now holds",
            )
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aReentrantRecomposeAnswersWithoutRecomposingAgain() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var recomposeCount = 0
        var text by mutableStateOf("first")
        lateinit var composition: OnDemandComposition
        composition =
            OnDemandComposition(parentContext) {
                recomposeCount++
                Label(text)
            }
        try {
            assertEquals(1, recomposeCount, "the first pass composes once")

            var reentrant: Component? = null
            val outer =
                composition.recompose {
                    text = "second"
                    // A recompose invoked from inside this very recompose's own update.
                    reentrant = composition.recompose { text = "third" }
                }

            assertEquals(2, recomposeCount, "the reentrant call must not drive a second recompose")
            assertSame(outer, reentrant, "the reentrant call answers the component the outer call is producing")
            assertEquals(
                "second",
                (outer as JLabel).text,
                "the reentrant call's own write must never have run",
            )
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aDisposedCompositionSuppressesUpdatesAndAnswersNullFromRecompose() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val composition = OnDemandComposition(parentContext) { Label("content") }
        assertNotNull(composition.recompose {}, "content composes one component before disposal")

        composition.dispose()
        var updates = 0
        assertNull(
            composition.recompose { updates++ },
            "a disposed composition answers null",
        )
        assertEquals(0, updates, "a disposed composition must not execute its update callback")
    }

    @TestFactory
    fun aCompositionComposingASecondTopLevelComponentIsRefusedAndDisposed(): List<DynamicTest> =
        Constructor.entries.flatMap { constructor ->
            Pass.entries.flatMap { pass ->
                Teardown.entries.map { teardown ->
                    dynamicTest("${constructor.name}, ${pass.name}, ${teardown.name}") {
                        runComposeSwingTest {
                            lateinit var parentContext: CompositionContext
                            setContent { parentContext = rememberCompositionContext() }
                            var overflowing by mutableStateOf(pass.onConstruction)
                            val host = JPanel()
                            var composition: OnDemandComposition? = null

                            val failure =
                                assertFailsWith<IllegalStateException> {
                                    pass.drive(
                                        this,
                                        build = {
                                            constructor
                                                .create(parentContext, host) {
                                                    Label("first")
                                                    if (overflowing) Label("second")
                                                    // Composed last, so it is disposed first and stops the runtime
                                                    // before it releases either label.
                                                    teardown.failures.forEach { failure ->
                                                        DisposableEffect(Unit) { onDispose { throw failure } }
                                                    }
                                                }.also { composition = it }
                                        },
                                        write = { overflowing = true },
                                    )
                                }

                            assertEquals(
                                teardown.failures,
                                failure.suppressed.toList(),
                                "the refusal keeps the failed disposal",
                            )
                            assertEquals(0, host.componentCount, "the refusal disposed the composition, releasing both")
                            assertNull(
                                composition?.recompose { fail("a disposed composition runs no update") },
                                "the refusal is terminal",
                            )
                        }
                    }
                }
            }
        }

    @TestFactory
    fun aDisposalThatThrowsStillTakesTheComponentOutOfItsContainer(): List<DynamicTest> =
        Constructor.entries.map { constructor ->
            dynamicTest(constructor.name) {
                runComposeSwingTest {
                    lateinit var parentContext: CompositionContext
                    setContent { parentContext = rememberCompositionContext() }
                    val cleanup = IllegalStateException("effect disposal failed")
                    val host = JPanel()
                    val composition =
                        constructor.create(parentContext, host) {
                            Label("content")
                            // Disposed ahead of the label, so the failure stops the runtime before the label's release.
                            DisposableEffect(Unit) { onDispose { throw cleanup } }
                        }

                    assertSame(cleanup, assertFailsWith<IllegalStateException> { composition.dispose() })
                    assertEquals(0, host.componentCount, "the component leaves the container it is in")
                    assertNull(composition.recompose { fail("a disposed composition runs no update") })
                }
            }
        }

    @TestFactory
    fun aCallOffTheEventDispatchThreadIsRefused(): List<DynamicTest> = Constructor.entries.flatMap { constructor ->
        Operation.entries.map { operation ->
            dynamicTest("${constructor.name}, ${operation.name}") {
                runComposeSwingTest {
                    lateinit var parentContext: CompositionContext
                    setContent { parentContext = rememberCompositionContext() }
                    val host = JPanel()

                    fun create() = constructor.create(parentContext, host) { Label("content") }
                    val composition = create()
                    try {
                        val thrown =
                            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                                withContext(dispatcher) {
                                    runCatching { operation.run(composition, ::create) }.exceptionOrNull()
                                }
                            }

                        assertIs<IllegalStateException>(
                            thrown,
                            "a call off the event dispatch thread must be refused",
                        )
                        assertEquals(1, host.componentCount, "and it composed or removed nothing")
                    } finally {
                        composition.dispose()
                    }
                }
            }
        }
    }

    @Test
    fun parentSnapshotInvalidationsRecomposeTheCompositionUnderItsParentContext() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        var parentText by mutableStateOf("first")
        setContent {
            parentContext = rememberCompositionContext()
            Label(parentText)
        }

        val composition = OnDemandComposition(parentContext) { Label(parentText) }
        try {
            val child = composition.recompose {} as JLabel
            assertEquals("first", child.text, "the child starts with the parent snapshot state")

            parentText = "second"
            awaitIdle()

            assertEquals(
                "second",
                child.text,
                "a parent snapshot invalidation must recompose the child under its parent context",
            )
        } finally {
            composition.dispose()
        }
    }
}

/** When the content of a composition first breaks the rule under test. */
internal enum class Pass(
    val onConstruction: Boolean,
    private val run: suspend ComposeSwingTest.(build: () -> OnDemandComposition, write: () -> Unit) -> Unit,
) {
    OnConstruction(true, { build, _ -> build() }),
    InARecompose(false, { build, write -> build().recompose(write) }),
    InAParentDrivenPass(false, { build, write ->
        build()
        write()
        awaitIdle()
    }),
    ;

    /** Builds the composition in this pass, then runs [write] so that the pass this names applies it. */
    suspend fun drive(
        test: ComposeSwingTest,
        build: () -> OnDemandComposition,
        write: () -> Unit,
    ) = test.run(build, write)
}

/** The constructors of [OnDemandComposition]; the component of an unplaced one is put in the host by the caller. */
private enum class Constructor(
    val create: (
        CompositionContext,
        JPanel,
        @Composable @SwingComposable
        () -> Unit,
    ) -> OnDemandComposition,
) {
    Unplaced({ parent, host, content ->
        OnDemandComposition(parent, content).also { composition -> composition.recompose {}?.let(host::add) }
    }),
    Placed({ parent, host, content -> OnDemandComposition(parent, host, addingTo(host), content) }),
}

/** Whether a disposal callback in the content throws while the composition is torn down. */
private enum class Teardown(
    val failures: List<Throwable>,
) {
    Quiet(emptyList()),
    Failing(listOf(IllegalStateException("effect disposal failed"))),
}

/** The calls an [OnDemandComposition] refuses off the Event Dispatch Thread. */
private enum class Operation(
    val run: (composition: OnDemandComposition, construct: () -> OnDemandComposition) -> Unit,
) {
    Construct({ _, construct -> construct() }),
    Recompose({ composition, _ -> composition.recompose {} }),
    Dispose({ composition, _ -> composition.dispose() }),
}

/** A placement that adds each component to [host] and removes it again on release. */
internal fun addingTo(host: Container): SlotAttachment = SlotAttachment { _, component, _ ->
    host.add(component)
    ({ host.remove(component) })
}
