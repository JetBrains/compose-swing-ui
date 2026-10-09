package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JComponent

/** An animated container under `modifier` that shows its content once `shown`. */
internal typealias PaddedContainer = @Composable (modifier: SwingModifier, shown: Boolean) -> Unit

/** What the tests of an animated container in a `GridBagLayout` cell with internal padding share. */
internal object GridBagPadding {
    /** Where the content was on a run of frames: not composed, at its own size, short of it, or over it on an axis. */
    enum class Run { Absent, Own, Short, Over }

    /**
     * The size the content was laid out at on each frame, `null` where it was not composed, against its own preferred
     * and minimum size; the container's size once the transition ended, and whether it never shrank.
     */
    class Frames(
        val contentSizes: List<Dimension?>,
        val own: Dimension,
        val minimum: Dimension,
        val containerEnd: Dimension?,
        val containerGrows: Boolean,
    ) {
        val shownSizes: Set<Dimension> get() = contentSizes.filterNotNull().toSet()

        /** One [Run] for each run of frames at one size, so content that creeps toward its own size shows many. */
        val runs: List<Run>
            get() {
                val sizes = mutableListOf<Dimension?>()
                contentSizes.forEachIndexed { index, size -> if (index == 0 || size != sizes.last()) sizes += size }
                return sizes.map {
                    when (it) {
                        null -> Run.Absent
                        own -> Run.Own
                        else -> if (it.width <= own.width && it.height <= own.height) Run.Short else Run.Over
                    }
                }
            }

        fun fitsMinimum(size: Dimension): Boolean = size.width >= minimum.width && size.height >= minimum.height
    }

    fun visibility(
        enter: EnterTransition,
        content: @Composable () -> Unit = { OwnSizeContent() },
    ): PaddedContainer =
        { modifier, shown ->
            AnimatedVisibility(visible = shown, modifier = modifier, enter = enter, exit = ExitTransition.None) {
                content()
            }
        }

    /** An [AnimatedContent] that replaces a short label with [content] by [transform]. */
    fun replacing(
        transform: ContentTransform,
        content: @Composable () -> Unit = { OwnSizeContent() },
    ): PaddedContainer =
        { modifier, shown ->
            AnimatedContent(shown, modifier, transitionSpec = { transform }) { own ->
                if (own) content() else Label("Other")
            }
        }

    /** A label whose minimum size is its preferred size. */
    @Composable
    fun OwnSizeContent(modifier: SwingModifier = SwingModifier) {
        Label("", modifier = modifier.testTag(CONTENT).preferredSize(OwnSize).minimumSize(OwnSize))
    }

    /** Wrapping text of [OwnSize], whose minimum is the width of its longest word. */
    @Composable
    fun WrappingContent(modifier: SwingModifier = SwingModifier) {
        Label(
            "<html>several short words that wrap at a narrow width</html>",
            modifier = modifier.testTag(CONTENT).preferredSize(OwnSize),
        )
    }

    /** The size of a cell with the padding [Paddings] names [name] by, around content of [OwnSize]. */
    fun padded(name: String): Dimension {
        val pad = Paddings.getValue(name.substringBefore(','))
        return Dimension(OwnSize.width + pad.width, OwnSize.height + pad.height)
    }

    val Paddings =
        mapOf("no pad" to Dimension(0, 0), "ipadx" to Dimension(10, 0), "ipady" to Dimension(0, 10))
    val OwnSize = Dimension(120, 60)
    const val TRANSITION_MILLIS = 320
    const val CONTAINER = "container"
    const val CONTENT = "content"

    /**
     * Shows [container] in a padded cell, or hides it when [shown] is `false`, and records each frame until its
     * transition has finished. On a [roundTrip], the container starts shown and is hidden until its transition has
     * finished first.
     */
    fun observe(
        pad: Dimension,
        container: PaddedContainer,
        shown: Boolean = true,
        roundTrip: Boolean = false,
    ): Frames {
        lateinit var frames: Frames
        runComposeSwingTest {
            var visible by mutableStateOf(!shown || roundTrip)
            setContent {
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(400, 300)) {
                        container(SwingModifier.item(ipadx = pad.width, ipady = pad.height).testTag(CONTAINER), visible)
                    }
                }
            }
            // The content at rest: before it exits, or once it entered. Wrapping text answers a minimum height for the
            // width it is laid out at.
            var atRest = onAllNodesWithTag(CONTENT).fetchAll<JComponent>().singleOrNull()
            val own = atRest?.preferredSize
            val minimum = atRest?.minimumSize
            mainClock.autoAdvance = false
            if (roundTrip) {
                visible = false
                repeat(TRANSITION_MILLIS / 16 + 8) {
                    mainClock.advanceTimeByFrame()
                    awaitIdle()
                }
            }
            visible = shown
            val contentSizes = mutableListOf<Dimension?>()
            val containerSizes = mutableListOf<Dimension>()
            // Frames enough to run the transition through and settle, also where the content's size moved part of the
            // way through and the size change runs on towards it.
            repeat(TRANSITION_MILLIS / 16 + 8) {
                mainClock.advanceTimeByFrame()
                awaitIdle()
                // The container is composed once shown, and the content while the transition shows it.
                onAllNodesWithTag(CONTAINER).fetchAll<JComponent>().forEach { holder -> containerSizes += holder.size }
                val label = onAllNodesWithTag(CONTENT).fetchAll<JComponent>().singleOrNull()
                contentSizes += label?.size
                if (own == null) atRest = label
            }
            val grows = containerSizes.zipWithNext().all { (a, b) -> b.width >= a.width && b.height >= a.height }
            frames =
                Frames(
                    contentSizes,
                    own ?: checkNotNull(atRest).preferredSize,
                    minimum ?: checkNotNull(atRest).minimumSize,
                    containerSizes.lastOrNull(),
                    grows,
                )
        }
        return frames
    }
}
