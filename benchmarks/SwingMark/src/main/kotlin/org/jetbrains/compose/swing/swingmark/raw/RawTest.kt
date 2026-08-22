package org.jetbrains.compose.swing.swingmark.raw

import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.swingmark.harness.Protocol
import org.jetbrains.compose.swing.swingmark.harness.Step
import org.jetbrains.compose.swing.swingmark.harness.SwingMarkTest
import org.jetbrains.compose.swing.swingmark.harness.Watchdog
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * A SwingMark test that builds Swing widgets and calls their setters, as the JDK's own suite does.
 *
 * The counterpart of `AbstractSwingTest`: [testComponent] is its `getTestComponent`, [paintCount] its
 * `paintCount`, which each test's `Count`-prefixed widget raises from its own `paint`. Every test here is
 * its original translated into Kotlin, down to which changes are posted and which are waited for.
 */
internal abstract class RawTest : SwingMarkTest {
    /**
     * Paints this test's own widget has made. Raised by the widget, read by the suite.
     *
     * A test whose original counts no paints leaves this at zero, as its original leaves it.
     */
    var paintCount: Int = 0

    override val paints: Int get() = paintCount

    override fun resetPaints() {
        paintCount = 0
    }

    /** Builds the widgets this test drives, and answers with the component holding them. */
    protected abstract fun testComponent(): JComponent

    override fun mount(card: JPanel): DisposableHandle {
        val component = testComponent()
        card.add(component, BorderLayout.CENTER)
        return DisposableHandle { card.remove(component) }
    }

    /** Posts [action] to the event dispatch thread, where the original's tests call `invokeLater`. */
    protected fun post(action: Runnable) {
        Protocol.record(Step.POST)
        Watchdog.progress()
        SwingUtilities.invokeLater(action)
    }

    /**
     * Runs [action] on the event dispatch thread and waits, where the original calls `invokeAndWait`.
     *
     * A phase built out of these never waits on the queue, so it reports its own progress: the watchdog
     * would otherwise read a long phase of them as a run that had stopped.
     */
    protected fun postAndWait(action: Runnable) {
        Protocol.record(Step.POST_AND_WAIT)
        Watchdog.progress()
        SwingUtilities.invokeAndWait(action)
    }
}

/** The client property a viewport scrolls by blitting under, which `-blit` sets as the original sets it. */
internal const val ENABLE_WINDOW_BLIT = "EnableWindowBlit"
