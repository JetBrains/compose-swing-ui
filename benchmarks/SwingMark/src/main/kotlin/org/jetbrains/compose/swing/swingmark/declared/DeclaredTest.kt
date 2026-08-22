package org.jetbrains.compose.swing.swingmark.declared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.swingmark.harness.PaintCounter
import org.jetbrains.compose.swing.swingmark.harness.SwingMarkTest
import org.jetbrains.compose.swing.swingmark.harness.onEventThread
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel

/**
 * A SwingMark test whose screen is declared through compose-swing-ui.
 *
 * The screen is [Content]; a change is a state write, settled through
 * `change`. The widgets under it are the library's, so a test reads
 * them through [widget] rather than holding them.
 *
 * A test that scrolls asks through [revealOnApply], so the scroll lands in the turn that applies the
 * change rather than in one of its own.
 */
internal abstract class DeclaredTest : SwingMarkTest {
    /** The screen this test shows. */
    @Composable
    abstract fun Content()

    /** The card this test's content is composed into. */
    protected lateinit var card: JPanel

    private var revealRequest by mutableStateOf<Reveal?>(null)

    /**
     * Asks for [perform] to run in the turn that applies the change being declared.
     *
     * This is where a screen built with setters scrolls: it mutates the widget and scrolls it in one
     * runnable, so both dirty regions reach the repaint manager in a single flush. A scroll made after
     * the change had settled would be a second flush, and the arm would be charged for a paint its
     * counterpart never makes.
     *
     * Each call is its own request, so asking twice for the same target is two scrolls: what is timed
     * is the scroll a change makes, not whether the viewport already stands where it would land.
     */
    protected fun revealOnApply(perform: () -> Unit) {
        revealRequest = Reveal(perform)
    }

    /**
     * Composes [Content] into [card], on a runtime of the library's own.
     *
     * The runtime is what decides when a state write reaches the widgets, so a suite that stood one up
     * for itself would be timing its own scheduling rather than the library's. This is the runtime an
     * application's content is driven by: it recomposes on the event queue, and asks for the frame that
     * carries a write the moment the recomposer waits for one.
     *
     * Stood up for the card rather than taken from its window, because the card is also laid out
     * offscreen, with no window anywhere, by the gate that compares the two arms.
     *
     * A test whose original holds a widget the library cannot declare overrides this, and returns a
     * handle covering both.
     */
    @OptIn(InternalSwingUiApi::class)
    override fun mount(card: JPanel): DisposableHandle {
        this.card = card
        val runtime = SwingRecomposer.create(card)
        val mounted =
            card.setContent(parent = runtime.compositionContext) {
                Content()
                RevealOnApply()
            }
        return DisposableHandle {
            mounted.dispose()
            runtime.dispose()
        }
    }

    /**
     * Carries out the standing [revealOnApply] request as the change that made it is applied.
     *
     * The request is read here rather than in [Content] so that asking for a scroll invalidates this
     * alone: a screen that recomposed whole because something asked it to scroll would be measuring
     * that instead.
     */
    @Composable
    private fun RevealOnApply() {
        val request = revealRequest
        SideEffect { request?.perform?.invoke() }
    }

    /**
     * Paints counted at the repaint manager.
     *
     * SwingMark counts them by subclassing the widget under test, which a declared screen has no way to
     * do: the widget is the library's. The two figures agree where one widget repaints itself, and not
     * where a viewport scrolls.
     */
    override val paints: Int get() = PaintCounter.paints

    override fun resetPaints() = PaintCounter.reset()

    /**
     * The widget the library built for this test's declaration.
     *
     * A test writes state but waits on the widget: state carries a change the moment it is written, the
     * widget only once the recomposition has run. Resolve it once, before the test's loop.
     */
    fun <T : Component> widget(type: Class<T>): T =
        onEventThread {
            checkNotNull(descendant(card, type)) {
                "$testName declared no ${type.simpleName}, so there is nothing for it to drive"
            }
        }
}

/**
 * One request to bring something into view. Two requests naming the same target are distinct, so
 * declaring the same scroll twice asks for it twice.
 */
private class Reveal(
    val perform: () -> Unit,
)

/** The first descendant of [parent] of type [type], depth first, or null when there is none. */
private fun <T : Component> descendant(
    parent: Container,
    type: Class<T>,
): T? {
    for (child in parent.components) {
        val found =
            type.takeIf { it.isInstance(child) }?.cast(child)
                ?: (child as? Container)?.let { descendant(it, type) }
        if (found != null) return found
    }
    return null
}
