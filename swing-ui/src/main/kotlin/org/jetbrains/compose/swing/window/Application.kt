@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.compose.swing.core.GlobalSnapshotManager
import org.jetbrains.compose.swing.core.SwingFrameClock
import kotlin.system.exitProcess

/**
 * An entry point for the Compose application. See [awaitApplication] for more information.
 *
 * Usually this entry point is used inside the `main()` function:
 * ```
 * fun main() = application {
 *
 * }
 * ```
 *
 * After all windows are closed and all operations are completed, the application ends.
 * Set [exitProcessOnExit] to `false` if you need to execute some code after the [application]
 * block; otherwise that code won't be executed, as [application] exits the process.
 *
 * This entry point is a blocking operation (it blocks the current thread until the application
 * finishes) and can't be called on the UI thread. To launch a new application from the UI thread
 * (for example, from some event listener), use [launchApplication] instead.
 *
 * The application can launch background tasks using [androidx.compose.runtime.LaunchedEffect]
 * or create [Window], [Dialog], or [org.jetbrains.compose.swing.components.Tray] in a declarative
 * Compose way:
 *
 * ```
 * fun main() = application {
 *     var isSplashScreenShowing by remember { mutableStateOf(true) }
 *
 *     LaunchedEffect(Unit) {
 *         delay(2000)
 *         isSplashScreenShowing = false
 *     }
 *
 *     if (isSplashScreenShowing) {
 *         Window(::exitApplication, title = "Splash") {}
 *     } else {
 *         Window(::exitApplication, title = "App") {}
 *     }
 * }
 * ```
 *
 * When there are no active compositions left, this function ends.
 * An active composition is one that has an active coroutine (for example, launched in
 * [androidx.compose.runtime.LaunchedEffect]) or a child composition created inside [Window],
 * [Dialog], or [org.jetbrains.compose.swing.components.Tray].
 *
 * @param exitProcessOnExit whether `exitProcess(0)` is called after the application is closed.
 * The explicit exit ends the process immediately instead of waiting for background threads to wind
 * down. If `false`, the execution of the function is unblocked after the application exits
 * (when the last window is closed, and all [androidx.compose.runtime.LaunchedEffect]s are complete).
 * @param content the application's content, composed against an [ApplicationScope].
 * @see [awaitApplication]
 */
public fun application(
    exitProcessOnExit: Boolean = true,
    content: @Composable ApplicationScope.() -> Unit,
) {
    runBlocking {
        awaitApplication {
            content()
        }
    }

    if (exitProcessOnExit) {
        exitProcess(0)
    }
}

/**
 * Short variant of launching an application inside a [CoroutineScope].
 *
 * This function is equivalent to:
 * ```
 * CoroutineScope.launch {
 *     awaitApplication {
 *
 *     }
 * }
 * ```
 *
 * Don't use `GlobalScope.launchApplication {}` to launch an application inside the `main()`
 * function without waiting for it to end: it does not block the main thread, so the application
 * process may stop before any window appears.
 *
 * @see [awaitApplication]
 */
public fun CoroutineScope.launchApplication(content: @Composable ApplicationScope.() -> Unit): Job =
    launch {
        awaitApplication(content = content)
    }

/**
 * An entry point for the Compose application.
 *
 * The application can launch background tasks using [androidx.compose.runtime.LaunchedEffect]
 * or create [Window], [Dialog], or [org.jetbrains.compose.swing.components.Tray] in a declarative
 * Compose way:
 *
 * ```
 * fun main() = runBlocking {
 *     awaitApplication {
 *         var isSplashScreenShowing by remember { mutableStateOf(true) }
 *
 *         LaunchedEffect(Unit) {
 *             delay(2000)
 *             isSplashScreenShowing = false
 *         }
 *
 *         if (isSplashScreenShowing) {
 *             Window(::exitApplication, title = "Splash") {}
 *         } else {
 *             Window(::exitApplication, title = "App") {}
 *         }
 *     }
 * }
 * ```
 *
 * When there are no active compositions left, this function ends.
 * An active composition is one that has an active coroutine (for example, launched in
 * [androidx.compose.runtime.LaunchedEffect]) or a child composition created inside [Window],
 * [Dialog], or [org.jetbrains.compose.swing.components.Tray].
 *
 * Animations driven in this composition (for example via [androidx.compose.runtime.withFrameNanos]
 * or `org.jetbrains.compose.swing.animation.core.animateFloatAsState`) advance on the application's
 * frame clock, which ticks at a fixed nominal rate. For animation paced to a display's refresh
 * rate, drive it in a composition mounted with [org.jetbrains.compose.swing.setContent], whose
 * frame clock follows the hosting window's display.
 *
 * [Window] and [Dialog] created inside [content] run as part of this application composition:
 * application-scope state and any [androidx.compose.runtime.CompositionLocal] provided in [content]
 * flow into their content.
 */
public suspend fun awaitApplication(content: @Composable ApplicationScope.() -> Unit) {
    withContext(Dispatchers.Swing) {
        GlobalSnapshotManager.ensureStarted()

        val recomposer = Recomposer(coroutineContext)
        val frameClock = SwingFrameClock(recomposer)
        try {
            var isOpen by mutableStateOf(true)

            val applicationScope = ApplicationScopeImpl(recomposer) { isOpen = false }

            coroutineScope {
                launch(frameClock) {
                    recomposer.runRecomposeAndApplyChanges()
                }

                launch {
                    val applier = ApplicationApplier()
                    val composition = Composition(applier, recomposer)
                    try {
                        composition.setContent {
                            if (isOpen) {
                                applicationScope.content()
                            }
                        }
                        recomposer.close()
                        recomposer.join()
                    } finally {
                        composition.dispose()
                    }
                }
            }
        } finally {
            frameClock.dispose()
        }
    }
}

/**
 * Scope used by [application], [awaitApplication] and [launchApplication].
 */
@Stable
public sealed interface ApplicationScope {
    /**
     * Close all windows created inside the application and cancel all launched effects
     * (they launch via [androidx.compose.runtime.LaunchedEffect] and
     * [androidx.compose.runtime.rememberCoroutineScope]).
     */
    public fun exitApplication()

    /**
     * The [Recomposer] driving this application's composition, and every window, dialog and tray
     * declared in it.
     *
     * `Recomposer.observe(CompositionRegistrationObserver)` registers on it to be told as each of those
     * compositions is registered and unregistered. A composition registers with the recomposer at the
     * root of its context chain, so this one reports every island inside a declared window as well.
     */
    public val recomposer: Recomposer
}

private class ApplicationScopeImpl(
    override val recomposer: Recomposer,
    private val exit: () -> Unit,
) : ApplicationScope {
    override fun exitApplication() {
        exit()
    }
}

private class ApplicationApplier : Applier<Any> {
    override val current: Any = Unit

    override fun down(node: Any) = Unit

    override fun up() = Unit

    override fun insertTopDown(
        index: Int,
        instance: Any,
    ) {
        check(instance is Unit) {
            "Composable content may not be added directly into " +
                ApplicationScope::class.simpleName
        }
    }

    override fun insertBottomUp(
        index: Int,
        instance: Any,
    ) {
        check(instance is Unit) {
            "Composable content may not be added directly into " +
                ApplicationScope::class.simpleName
        }
    }

    override fun remove(
        index: Int,
        count: Int,
    ) = Unit

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) = Unit

    override fun clear() = Unit

    override fun onEndChanges() = Unit
}
