package org.jetbrains.compose.swing.core

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.window.LocalWindow
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.awaitApplication
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for the application-scope recomposer boundary: windows declared in an application
 * compose on the recomposer its scope hands out and are refused a recomposer of their own.
 */
class ApplicationRecomposerTest {
    @Test
    fun aWindowComposingUnderAnApplicationIsRefusedARecomposerOfItsOwn() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        var refusal: Throwable? = null
        var recomposerMinted = true
        var foundOnTheWindow: Recomposer? = null
        var applicationRecomposer: Recomposer? = null

        withTimeout(SETTLE_TIMEOUT) {
            awaitApplication {
                applicationRecomposer = recomposer
                Window(onCloseRequest = ::exitApplication, visible = false) {
                    val peer = LocalWindow.current
                    LaunchedEffect(peer) {
                        // The application's effects run on the EDT, which is where a window is asked
                        // what recomposer it has.
                        refusal = runCatching { peer?.compositionContext() }.exceptionOrNull()
                        recomposerMinted = peer?.swingRecomposerOrNull() != null
                        foundOnTheWindow = peer?.findRecomposer()
                        exitApplication()
                    }
                }
            }
        }

        val thrown =
            assertNotNull(refusal, "a window whose content is the application's owns no recomposer to hand out")
        assertIs<IllegalStateException>(thrown, "the refusal must be reported as illegal state")
        assertTrue(
            "ApplicationScope" in thrown.message.orEmpty(),
            "the refusal must name where the recomposer really is, but was: ${thrown.message}",
        )
        assertFalse(
            recomposerMinted,
            "asking must not answer by creating a recomposer that drives nothing",
        )
        assertSame(
            applicationRecomposer,
            foundOnTheWindow,
            "such a window drives no recomposer of its own, so it answers with the application's - the " +
                "one ApplicationScope hands out",
        )
    }

    /**
     * The application's own composition is registered on the recomposer its scope hands out. Needs no
     * window, so it holds where there is no display - which is where a recomposer of its own, driving
     * nothing, would go unnoticed.
     */
    @OptIn(ExperimentalComposeRuntimeApi::class)
    @Test
    fun anApplicationComposesOnTheRecomposerItsScopeHandsOut() = runBlocking {
        val registered = compositionCollector()
        var reported = 0

        withTimeout(SETTLE_TIMEOUT) {
            awaitApplication {
                LaunchedEffect(Unit) {
                    val handle = recomposer.observe(registered.observer)
                    try {
                        reported = registered.compositions.size
                    } finally {
                        handle.dispose()
                    }
                    exitApplication()
                }
            }
        }

        assertTrue(
            reported >= 1,
            "the application's own composition must be registered on the recomposer its scope hands out, " +
                "but that recomposer reported $reported compositions",
        )
    }

    @OptIn(ExperimentalComposeRuntimeApi::class)
    @Test
    fun aWindowDeclaredInAnApplicationComposesOnTheRecomposerItsScopeHandsOut() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")

        // A composition registers with the recomposer at the root of its context chain. So a registration
        // arriving at an observer of ApplicationScope.recomposer exactly when a window is declared is what
        // says that window's content composes on that very recomposer: the scope hands out the one the
        // window runs on, not a recomposer of its own that drives nothing.
        val registered = compositionCollector()
        var beforeWindow = 0
        var afterWindow = 0

        withTimeout(SETTLE_TIMEOUT) {
            awaitApplication {
                var isWindowDeclared by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val handle = recomposer.observe(registered.observer)
                    try {
                        beforeWindow = registered.compositions.size
                        isWindowDeclared = true
                        awaitCancellation()
                    } finally {
                        handle.dispose()
                    }
                }

                if (isWindowDeclared) {
                    Window(onCloseRequest = ::exitApplication, visible = false) {
                        Label(text = "in-application")
                        LaunchedEffect(Unit) {
                            // The window's content is mounted by now, so its composition has registered.
                            afterWindow = registered.compositions.size
                            exitApplication()
                        }
                    }
                }
            }
        }

        assertTrue(
            beforeWindow >= 1,
            "the application's own composition must be registered on the recomposer its scope hands out, " +
                "but that recomposer reported $beforeWindow compositions",
        )
        assertTrue(
            afterWindow > beforeWindow,
            "declaring a window must register its content composition on the recomposer the application's " +
                "scope hands out, but that recomposer went from $beforeWindow compositions to $afterWindow",
        )
    }

    /** Collects the compositions a [CompositionRegistrationObserver] is told about, newest last. */
    private fun compositionCollector(): CompositionCollector = CompositionCollector()

    private companion object {
        val SETTLE_TIMEOUT = 10.seconds
    }
}

/** Every composition an observer has been told about and not yet been told was unregistered. */
@OptIn(ExperimentalComposeRuntimeApi::class)
private class CompositionCollector {
    val compositions: MutableList<ObservableComposition> = mutableListOf()

    val observer: CompositionRegistrationObserver =
        object : CompositionRegistrationObserver {
            override fun onCompositionRegistered(composition: ObservableComposition) {
                compositions += composition
            }

            override fun onCompositionUnregistered(composition: ObservableComposition) {
                compositions.remove(composition)
            }
        }
}
