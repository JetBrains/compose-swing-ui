package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.core.SwingRecomposer
import java.awt.Container
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.RepaintManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Mounts [content], takes the single widget it declares off [declared] as the user would, and asserts
 * that the user is never shown a value the caller did not adopt: the change provokes a paint, and the
 * widget holds [declared] at that one and at every paint after it.
 *
 * A change reaches the screen through a repaint Swing asks for while the widget is still handling it,
 * ahead of anything the report of that change can schedule. So a put-back that only reaches the widget on
 * a later event-dispatch cycle - which is what [assertUnadoptedChangeIsPutBack] bounds - is still one the
 * user sees flash past. This pins the stronger property: the put-back lands before that repaint is served.
 *
 * Repaints are counted through a [RepaintManager] of the test's own, installed once the widget is
 * mounted and holding [declared], and restored before this returns. It stands in for the real one on the
 * one property under test: a repaint is served from a later event than the one that asked for it.
 *
 * [content] is handed the report to call from the callback the component under test reports changes
 * through. Calling it adopts nothing - it only states that the widget told the caller it had changed,
 * which is what says the gesture landed rather than being one the widget ignores.
 *
 * [change] must be the user's own gesture - the events the toolkit delivers for it, each from an
 * event-queue cycle of its own, as [click], [drag] and [type] deliver them. A change that writes the
 * widget's API instead, or that runs a whole gesture inside a single cycle, hides every ordering this
 * measures: nothing the recomposer or Swing queues can land between the change and the settlement, so the
 * assertion holds whether or not the property does. An API write is honest only for a widget whose
 * mechanism is its own synchronous report.
 *
 * @param type the class the widget under test is built as
 * @param declared the value [content] declares, which the widget must be holding at every paint
 * @param content the content under test, declaring exactly one component
 * @param change the user's own change, made on the widget itself
 * @param read the widget property [declared] is measured against
 */
internal suspend fun <C : JComponent> assertUnadoptedChangeIsNeverPainted(
    type: Class<C>,
    declared: Any?,
    content: @Composable (report: () -> Unit) -> Unit,
    change: suspend (C) -> Unit,
    read: (C) -> Any?,
): Unit = assertUnadoptedChangeIsNeverPainted(type, declared, content, UserChange(made = change), read)

/**
 * [assertUnadoptedChangeIsNeverPainted], for a change the user makes across two events - text typed into
 * a field that only commits its edit when focus leaves it. Only the paints [UserChange.made] provokes are
 * counted; see [UserChange].
 */
internal suspend fun <C : JComponent> assertUnadoptedChangeIsNeverPainted(
    type: Class<C>,
    declared: Any?,
    content: @Composable (report: () -> Unit) -> Unit,
    change: UserChange<C>,
    read: (C) -> Any?,
) {
    val composition = JPanel()
    val recomposer = SwingRecomposer.create(composition)
    var mounted: DisposableHandle? = null
    val standingManager = RepaintManager.currentManager(composition)
    try {
        var reported = false
        mounted = composition.setContent(parent = recomposer.compositionContext) { content { reported = true } }
        val widget = singleDescendant(composition, type)
        assertEquals(declared, read(widget), "the widget must mount holding what the content declares")
        // A widget still sized at nothing asks for no repaint at all, so the composition is laid out
        // before the change is made.
        composition.setSize(composition.preferredSize)
        composition.layoutDeeply()
        change.earlier(widget)
        assertEquals(
            declared,
            read(widget),
            "what the user did before the change must leave the declaration standing",
        )

        val repaints = RecordedRepaints(value = { read(widget) }, paint = ::paintOffscreen)
        RepaintManager.setCurrentManager(repaints)
        change.made(widget)
        assertTrue(
            reported,
            "the change must reach the ${type.simpleName} and be reported before paints are counted",
        )
        repeat(PAINT_CYCLES) { yield() }

        assertTrue(repaints.served.isNotEmpty(), "the change must provoke a paint of the ${type.simpleName}")
        assertEquals(
            emptyList(),
            repaints.served.filter { shown -> shown != declared },
            "a change the caller does not adopt must be off the ${type.simpleName} before the paint it " +
                "asked for is served, so that the user is shown the declaration and nothing else; the paints " +
                "showed ${repaints.served}",
        )
    } finally {
        RepaintManager.setCurrentManager(standingManager)
        mounted?.dispose()
        recomposer.dispose()
    }
}

/**
 * A change the user makes on the widget, in the events they make it in.
 *
 * [earlier] is what they did before the change under test and have already been shown - typing into a
 * field that only commits its edit when focus leaves it. It runs before the paints are counted, so the
 * paints it asks for are served by the standing repaint manager: they are paints the user has already
 * seen, and counting them would measure them against a declaration [made] had not yet taken the widget
 * off. It must leave the widget holding the declaration, which is what says the earlier event changed
 * nothing the caller declared.
 *
 * [made] is the event under test, and the paints counted are the ones it provokes.
 */
internal class UserChange<C>(
    val made: suspend (C) -> Unit,
    val earlier: suspend (C) -> Unit = {},
)

/**
 * Lays this container out and every container under it, so that each descendant is left with the bounds
 * its own layout gives it.
 *
 * A realized tree reaches this state through [Container.validate], which an unrealized one - a container
 * with no peer, which is every composition here - answers with nothing at all. Walking it by hand is what
 * gives a nested widget the area it needs to ask for a repaint: the editor a spinner holds, or an option
 * standing in a group's own panel, is laid out by its parent rather than by the composition.
 */
private fun Container.layoutDeeply() {
    doLayout()
    components.filterIsInstance<Container>().forEach { it.layoutDeeply() }
}

/**
 * The one component of [type] anywhere under [composition], failing where the count is not exactly one.
 *
 * Content is reached this way rather than as the composition's own child, because content that arranges
 * its widgets in a panel of its own - a group of options, say - declares that panel as the child.
 * Requiring exactly one keeps the guard that child carried: content under test declares one widget.
 */
internal fun <C : JComponent> singleDescendant(
    composition: JPanel,
    type: Class<C>,
): C {
    val found =
        buildList {
            fun walk(container: Container) {
                container.components.forEach { child ->
                    if (type.isInstance(child)) add(type.cast(child))
                    if (child is Container) walk(child)
                }
            }
            walk(composition)
        }
    return found.singleOrNull()
        ?: fail("The content must declare exactly one ${type.simpleName}, and declared ${found.size}")
}

/** The single component [composition] was mounted with, cast to [type], failing where it is neither. */
internal fun <C> singleWidget(
    composition: JPanel,
    type: Class<C>,
): C {
    val child =
        composition.components.singleOrNull()
            ?: fail("The content must declare exactly one component, and declared ${composition.componentCount}")
    assertTrue(
        type.isInstance(child),
        "The content must declare a ${type.simpleName}, and declared a ${child.javaClass.simpleName}",
    )
    return type.cast(child)
}

/**
 * Paints [component] into an image of its own, so the paint the user would see is a paint that really
 * runs - and a look and feel that reads the widget's state as it renders reads the state recorded
 * alongside it.
 */
private fun paintOffscreen(component: JComponent) {
    val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        component.paint(graphics)
    } finally {
        graphics.dispose()
    }
}

/**
 * The event-dispatch cycles a change is given for the paints it provokes to run.
 *
 * The paint is served from the cycle right after the event that made the change. The rest are room for the
 * put-back to arrive late and paint a second time, which is the failure this assertion exists to catch:
 * too few cycles here would let that second paint go uncounted and pass.
 */
private const val PAINT_CYCLES: Int = 5
