package org.jetbrains.compose.swing

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.util.concurrent.Executors
import javax.swing.JButton
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The contract of [OnDemandComposition]: it recomposes only on [OnDemandComposition.recompose], hands
 * back the one top-level component its content composes, terminally refuses more than one, answers a
 * reentrant call without recomposing again, and answers `null` once disposed.
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
    fun aRecomposeThatComposesASecondTopLevelComponentIsTerminal() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var showSecond by mutableStateOf(false)
        val composition =
            OnDemandComposition(parentContext) {
                Label("first")
                if (showSecond) Label("second")
            }
        try {
            assertEquals("first", (composition.recompose {} as JLabel).text)

            val failure =
                assertFailsWith<MultipleTopLevelComponentsException> {
                    composition.recompose { showSecond = true }
                }
            assertContains(
                failure.message.orEmpty(),
                "top-level component",
                message = "the failure must say what content is refused for composing too many of",
            )

            var updates = 0
            assertNull(
                composition.recompose { updates++ },
                "a composition that overflowed its root is terminally disposed",
            )
            assertEquals(0, updates, "a terminally disposed composition must not execute its update callback")
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aParentSnapshotOverflowIsTerminal() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var showSecond by mutableStateOf(false)
        val composition =
            OnDemandComposition(parentContext) {
                Label("first")
                if (showSecond) Label("second")
            }
        try {
            assertEquals("first", (composition.recompose {} as JLabel).text)

            showSecond = true
            val failure = assertFailsWith<MultipleTopLevelComponentsException> { awaitIdle() }
            assertContains(
                failure.message.orEmpty(),
                "top-level component",
                message = "the parent-driven refusal must name the invalid root",
            )

            var updates = 0
            assertNull(
                composition.recompose { updates++ },
                "a composition that overflows from its parent is terminally disposed",
            )
            assertEquals(0, updates, "a terminally disposed composition must not execute updates")
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

    @Test
    fun aRecomposeOffTheEventDispatchThreadIsRefused() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val composition = OnDemandComposition(parentContext) { Label("content") }
        try {
            val thrown =
                Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { offTheEventDispatchThread ->
                    withContext(offTheEventDispatchThread) {
                        runCatching { composition.recompose {} }.exceptionOrNull()
                    }
                }

            assertIs<IllegalStateException>(thrown, "a recompose off the event dispatch thread must be refused")
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aDisposeOffTheEventDispatchThreadIsRefused() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val composition = OnDemandComposition(parentContext) { Label("content") }
        try {
            val thrown =
                Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { offTheEventDispatchThread ->
                    withContext(offTheEventDispatchThread) {
                        runCatching { composition.dispose() }.exceptionOrNull()
                    }
                }

            assertIs<IllegalStateException>(thrown, "a dispose off the event dispatch thread must be refused")
        } finally {
            composition.dispose()
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
