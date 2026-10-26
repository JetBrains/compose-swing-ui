// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.SwingTestScope
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.util.fastForEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.test.assertTrue

/** Who owns the recomposer content composes on. */
internal enum class RecomposerOwner {
    /** The window the content stands in: this library creates the recomposer and ends it. */
    Window,

    /** A caller who obtained the recomposer through [SwingRecomposer.create] and names it as the parent. */
    Caller,
}

/**
 * Content containers that compose on one recomposer of [owner]'s. The window's recomposer is created by
 * the first content set here; the caller's is created with this host. Must be used on the EDT, and a
 * [RecomposerOwner.Window] host needs a display.
 */
internal class RecomposerHost(
    val owner: RecomposerOwner,
) {
    private val frame: JFrame? =
        if (owner == RecomposerOwner.Window) {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            realizedFrame()
        } else {
            null
        }
    private val created: SwingRecomposer? = if (frame == null) SwingRecomposer.create(JPanel()) else null
    private val handles = mutableListOf<DisposableHandle>()

    /** The recomposer content set here composes on, or `null` while the window holds none. */
    val recomposer: SwingRecomposer?
        get() = created ?: frame?.swingRecomposerOrNull()

    /** Sets [content] on a new container and returns that container; a first pass's failure propagates. */
    fun setContent(content: @Composable () -> Unit): JPanel {
        val panel = JPanel()
        if (frame != null) {
            frame.contentPane.add(panel)
            handles += panel.setContent(content = content)
        } else {
            handles += panel.setContent(parent = checkNotNull(created).compositionContext, content = content)
        }
        return panel
    }

    /** Composes content here and waits until the recomposer runs it, so a later failure finds it running. */
    suspend fun startRunning() {
        setContent { Label(text = "running") }
        awaitUntil("the recomposer runs") { recomposer?.isRecomposing == true }
    }

    fun dispose() {
        handles.fastForEach { it.dispose() }
        created?.dispose()
        frame?.dispose()
    }
}

/** When a first composition fails, relative to the recomposer it composes on. */
internal enum class FirstCompositionTiming {
    /** In the event that creates the recomposer, before its coroutines have had a turn. */
    InTheCreatingEvent,

    /** After the recomposer's runner has started. */
    OnARunningRecomposer,
}

/** One case per owner in [owners], each run on the EDT through [runSwingTest]. */
internal fun perOwner(
    owners: List<RecomposerOwner> = RecomposerOwner.entries,
    case: suspend SwingTestScope.(RecomposerOwner) -> Unit,
): List<DynamicTest> = owners.map { owner -> DynamicTest.dynamicTest("$owner") { runSwingTest { case(owner) } } }

/** One case per owner in [owners] and timing in [timings], each run on the EDT through [runSwingTest]. */
internal fun perOwnerAndTiming(
    owners: List<RecomposerOwner> = RecomposerOwner.entries,
    timings: List<FirstCompositionTiming> = FirstCompositionTiming.entries,
    case: suspend SwingTestScope.(RecomposerOwner, FirstCompositionTiming) -> Unit,
): List<DynamicTest> = owners.flatMap { owner ->
    timings.map { timing ->
        DynamicTest.dynamicTest("$owner, $timing") { runSwingTest { case(owner, timing) } }
    }
}

/** Whether the runtime answers that this recomposer recomposes what invalidates. */
internal val SwingRecomposer.isRecomposing: Boolean
    get() = recomposer.currentState.value.let { it == Recomposer.State.Idle || it == Recomposer.State.PendingWork }

/**
 * Asserts that [reported] holds [failure] alone. A failure thrown out of a coroutine can arrive as the copy
 * coroutine stack-trace recovery makes of it, which carries the original as its cause.
 */
internal fun assertReportedOnce(
    failure: Throwable,
    reported: List<Throwable>,
    message: String,
) {
    val single = reported.singleOrNull()
    assertTrue(single != null && (single === failure || single.cause === failure), "$message, but reported: $reported")
}
