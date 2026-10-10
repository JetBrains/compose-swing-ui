package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.NaturalSize.FRAMES
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.GridBagConstraints
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JViewport
import kotlin.math.roundToInt

/**
 * The content's size [whileEntering] and once it has [settled], the size it had at the end of each frame from the enter
 * until the transition ended, in order and with a size that repeats [taken] once, and the extent of the viewport it is
 * in, if any.
 */
internal class Entering(
    val whileEntering: Dimension,
    val settled: Dimension,
    val taken: List<Dimension>,
    val viewportExtent: Dimension?,
)

/** Adds [size] unless the size last added is the same. */
internal fun MutableList<Dimension>.recordChange(size: Dimension) {
    if (lastOrNull() != size) add(size)
}

internal typealias StockParent = @Composable (child: @Composable (SwingModifier) -> Unit) -> Unit

/** An animated container under `modifier` that enters content declared with `contentModifier` once `shown`. */
internal typealias EnteringContainer =
    @Composable (modifier: SwingModifier, shown: Boolean, contentModifier: SwingModifier) -> Unit

internal typealias TaggedContent = @Composable ConstrainedScope.(modifier: SwingModifier) -> Unit

/** What the tests of an animated container directly under a stock parent share. */
internal object Stretch {
    fun visibility(
        enter: EnterTransition,
        content: TaggedContent,
    ): EnteringContainer =
        { modifier, shown, contentModifier ->
            AnimatedVisibility(visible = shown, modifier = modifier, enter = enter, exit = ExitTransition.None) {
                content(contentModifier)
            }
        }

    /** An [AnimatedContent] that replaces a short label with [content] by [transform]. */
    fun replacing(
        transform: ContentTransform,
        content: TaggedContent = WideContent,
    ): EnteringContainer =
        { modifier, shown, contentModifier ->
            AnimatedContent(shown, modifier, transitionSpec = { transform }) { wide ->
                if (wide) {
                    content(contentModifier)
                } else {
                    Label("Other", modifier = SwingModifier.preferredSize(NARROW / 4, 20))
                }
            }
        }

    /** Each grants its one child the whole of a fixed panel, whatever the child prefers. */
    val StretchingParents: Map<String, StockParent> =
        mapOf(
            "BoxLayout Y_AXIS" to { child ->
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.Box(BoxLayout.Y_AXIS), modifier = SwingModifier.preferredSize(NARROW, WIDE)) {
                        child(SwingModifier)
                    }
                }
            },
            "BoxLayout X_AXIS" to { child ->
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.Box(BoxLayout.X_AXIS), modifier = SwingModifier.preferredSize(NARROW, WIDE)) {
                        child(SwingModifier)
                    }
                }
            },
            "GridBagLayout fill BOTH" to { child ->
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(NARROW, WIDE)) {
                        child(SwingModifier.item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.BOTH))
                    }
                }
            },
        )

    /** Each grants its one child the size the child answers, with no space of its own. */
    val FollowingParents: Map<String, StockParent> =
        mapOf(
            "FlowLayout" to { child -> Panel(PanelLayout.Flow()) { child(SwingModifier) } },
            "BoxLayout Y_AXIS at its preferred size" to { child ->
                Panel(PanelLayout.Flow()) { Panel(PanelLayout.Box(BoxLayout.Y_AXIS)) { child(SwingModifier) } }
            },
        )

    /** Grants its north child the width of a fixed panel and the height the child answers. */
    val InNorthSlot: StockParent = { child ->
        Panel(PanelLayout.Flow()) {
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(NARROW, WIDE)) {
                child(SwingModifier.north())
            }
        }
    }

    val InViewport: StockParent = { child ->
        Panel(PanelLayout.Flow()) {
            ScrollPane(modifier = SwingModifier.preferredSize(NARROW, NARROW + NARROW / 4)) {
                Viewport(modifier = SwingModifier.testTag(VIEWPORT)) { child(SwingModifier) }
            }
        }
    }

    /** Prefers [WIDE] by 20, and keeps a 16:9 aspect ratio at the width it is offered. */
    val WideContent: TaggedContent = { modifier ->
        Label("", modifier = modifier.aspectRatio(RATIO).preferredSize(WIDE, 20))
    }

    /** Prefers the width of its text, and fills the width it is offered at a 16:9 aspect ratio. */
    val FillingContent: TaggedContent = { modifier ->
        Label(FILLING_TEXT, modifier = modifier.fillMaxWidth().aspectRatio(RATIO))
    }

    /** [FillingContent] measured with no maximum: its text's preferred size, and no extent for the ratio. */
    val FillingOwnSize: Dimension get() = JLabel(FILLING_TEXT).preferredSize

    fun atAspect(width: Int): Dimension = Dimension(width, (width / RATIO).roundToInt())

    const val RATIO = 16f / 9f
    private const val FILLING_TEXT = "Preview"
    const val NARROW = 160
    const val WIDE = 320
    const val TRANSITION_MILLIS = 320
    const val FRAME_MILLIS = 16
    const val VIEWPORT = "viewport"
    const val CONTENT = "content"

    fun underParents(
        parents: Map<String, StockParent>,
        containers: Map<String, EnteringContainer>,
    ): Map<String, Entering> =
        buildMap {
            for ((name, parent) in parents) {
                for ((kind, container) in containers) put("$name, $kind", observeEntering(parent, container))
            }
        }

    fun observeEntering(
        parent: StockParent,
        container: EnteringContainer,
    ): Entering {
        lateinit var entering: Entering
        runComposeSwingTest {
            var shown by mutableStateOf(false)
            val taken = mutableListOf<Dimension>()
            lateinit var whileEntering: Dimension
            var viewportExtent: Dimension? = null
            setContent { parent { modifier -> container(modifier, shown, SwingModifier.testTag(CONTENT)) } }
            mainClock.autoAdvance = false
            shown = true
            repeat(2 * TRANSITION_MILLIS / FRAME_MILLIS + FRAMES) { frame ->
                driveOneFrame()
                val size = onNodeWithTag(CONTENT).fetch().size
                taken.recordChange(size)
                // The third frame is inside the transition's delay, so the first layout of the size change is read.
                if (frame == 2) {
                    whileEntering = size
                    viewportExtent = onAllNodesWithTag(VIEWPORT).fetchAll<JViewport>().firstOrNull()?.extentSize
                }
            }
            entering = Entering(whileEntering, taken.last(), taken, viewportExtent)
        }
        return entering
    }

    /**
     * The sizes the content takes on each frame after it is resized from the first of [widths] to the second
     * part-way through the transition, until the transition is three quarters through.
     */
    fun resizedSizes(
        parent: StockParent,
        container: (TaggedContent) -> EnteringContainer,
        widths: Pair<Int, Int>,
    ): Set<Dimension> {
        val sizes = mutableSetOf<Dimension>()
        runComposeSwingTest {
            var shown by mutableStateOf(false)
            val width = mutableIntStateOf(widths.first)
            val entering =
                container { modifier ->
                    Label("", modifier = modifier.aspectRatio(RATIO).preferredSize(width.intValue, 20))
                }
            setContent { parent { modifier -> entering(modifier, shown, SwingModifier.testTag(CONTENT)) } }
            mainClock.autoAdvance = false
            shown = true
            repeat(TRANSITION_MILLIS / FRAME_MILLIS / 4) { driveOneFrame() }
            width.intValue = widths.second
            repeat(TRANSITION_MILLIS / FRAME_MILLIS / 2) {
                driveOneFrame()
                sizes += onNodeWithTag(CONTENT).fetch().size
            }
        }
        return sizes
    }
}
