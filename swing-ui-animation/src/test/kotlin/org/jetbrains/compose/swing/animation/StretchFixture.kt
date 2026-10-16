package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.cyclesUntilStable
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.GridBagConstraints
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JViewport
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * The sizes at the end of a frame: of the content, and of the container it stood in.
 *
 * @property content the size of the content.
 * @property container the size of the container.
 * @property corrections the layouts the frame's paint requested before the layout was stable.
 * @property resized whether the frame gave the container a size other than the one painted before it.
 */
internal class FrameSizes(
    val content: Dimension,
    val container: Dimension,
    val corrections: Int,
    val resized: Boolean,
)

/**
 * Asserts that no frame of [frames] had more layouts requested by its paint than its allocation allows: one where it
 * gave the container a size other than the one painted before it, none where it repeated that size.
 */
internal fun assertLayoutsSettle(frames: List<FrameSizes>) {
    assertEquals(
        emptyList(),
        frames.withIndex().filter { (_, frame) -> frame.corrections > (if (frame.resized) 1 else 0) }.map { it.index },
        "the frames whose paint requested more layouts than their allocation allows",
    )
}

/** Asserts that on each of [frames] the content held the aspect ratio within the room its container had. */
internal fun assertContentFitsContainer(frames: List<FrameSizes>) {
    assertEquals(
        frames.map { Stretch.atAspectWithin(it.container) },
        frames.map { it.content },
        "the content on each frame, for the size of its container on that frame",
    )
}

/**
 * An enter observed frame by frame until the transition ended.
 *
 * @property frames the sizes at the end of each frame from the enter.
 * @property viewportExtent the extent of the viewport the container is in on the third frame, or `null` where it is
 * in none.
 */
internal class Entering(
    val frames: List<FrameSizes>,
    val viewportExtent: Dimension?,
) {
    /** The content's size on the third frame, before an enter delayed by a transition's length has begun. */
    val whileEntering: Dimension get() = frames[2].content

    /** The content's size once the transition ended. */
    val settled: Dimension get() = frames.last().content

    /** The content's size on each frame it differed from the frame before, in order. */
    val taken: List<Dimension> =
        buildList {
            for (frame in frames) {
                if (lastOrNull() != frame.content) add(frame.content)
            }
        }
}

/** Each pair of consecutive sizes of which the second is smaller than the first on some axis. */
internal fun List<Dimension>.shrinkages(): List<Pair<Dimension, Dimension>> =
    zipWithNext().filter { (before, after) -> after.width < before.width || after.height < before.height }

/** Whether no size is smaller than the one before it on either axis. */
internal fun List<Dimension>.neverShrinks(): Boolean = shrinkages().isEmpty()

internal typealias StockParent = @Composable (child: @Composable (SwingModifier) -> Unit) -> Unit

/** An animated container under `modifier` that enters content declared with `contentModifier` once `shown`. */
internal typealias EnteringContainer =
    @Composable (modifier: SwingModifier, shown: Boolean, contentModifier: SwingModifier) -> Unit

internal typealias TaggedContent = @Composable ConstrainedScope.(modifier: SwingModifier) -> Unit

/** What the tests of an animated container directly under a stock parent share. */
internal object Stretch {
    const val RATIO = 16f / 9f
    const val NARROW = 160
    const val WIDE = 320
    const val TRANSITION_MILLIS = 320
    const val FRAME_MILLIS = 16
    const val CONTAINER = "animation"
    const val CONTENT = "content"

    /** Enough frames for an enter delayed by one transition's length to run to its end. */
    const val FRAMES_TO_SETTLE = 2 * TRANSITION_MILLIS / FRAME_MILLIS + 3

    /**
     * A stock parent, and the size content that prefers [WIDE] and holds the aspect ratio settles at in it.
     *
     * @property name what the parent is called in a test report.
     * @property parent the parent.
     * @property settled the size the content settles at in the parent.
     * @property assignsRoomFromFirstFrame whether the parent assigns its container the same room on every frame,
     * whatever the container requests.
     */
    class ParentCase(
        val name: String,
        val parent: StockParent,
        val settled: Dimension,
        val assignsRoomFromFirstFrame: Boolean = false,
    ) {
        override fun toString(): String = name
    }

    /** A stock parent whose content has its width changed from [from] to [to] part-way through an enter. */
    class ResizeCase(
        val name: String,
        val parent: ParentCase,
        val from: Int,
        val to: Int,
        val settled: Dimension,
    ) {
        override fun toString(): String = name
    }

    /**
     * The frames of an enter whose content is resized part-way through.
     *
     * @property all every frame from the enter.
     * @property afterResize the frames from the resize on.
     */
    class ResizedFrames(
        val all: List<FrameSizes>,
        val afterResize: List<FrameSizes>,
    )

    /** A change of [AnimatedContent]'s content. */
    class TransformCase(
        val name: String,
        val transform: ContentTransform,
    ) {
        override fun toString(): String = name
    }

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
    val StretchingParents: List<ParentCase> =
        listOf(
            ParentCase(
                "BoxLayout Y_AXIS",
                boxParent(BoxLayout.Y_AXIS),
                Dimension(160, 90),
                assignsRoomFromFirstFrame = true,
            ),
            ParentCase("BoxLayout X_AXIS", boxParent(BoxLayout.X_AXIS), Dimension(160, 90)),
            ParentCase(
                "GridBagLayout fill BOTH",
                { child ->
                    Panel(PanelLayout.Flow()) {
                        Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(NARROW, WIDE)) {
                            child(SwingModifier.item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.BOTH))
                        }
                    }
                },
                Dimension(160, 90),
                assignsRoomFromFirstFrame = true,
            ),
        )

    private fun boxParent(axis: Int): StockParent =
        { child ->
            Panel(PanelLayout.Flow()) {
                Panel(PanelLayout.Box(axis), modifier = SwingModifier.preferredSize(NARROW, WIDE)) {
                    child(SwingModifier)
                }
            }
        }

    val FlowLayoutParent =
        ParentCase(
            "FlowLayout",
            { child -> Panel(PanelLayout.Flow()) { child(SwingModifier) } },
            Dimension(320, 180),
        )

    private val BoxYAtPreferredSize =
        ParentCase(
            "BoxLayout Y_AXIS at its preferred size",
            { child ->
                Panel(PanelLayout.Flow()) { Panel(PanelLayout.Box(BoxLayout.Y_AXIS)) { child(SwingModifier) } }
            },
            Dimension(320, 180),
        )

    /** Each grants its one child the preferred size of the child, with no space of its own. */
    val FollowingParents: List<ParentCase> = listOf(FlowLayoutParent, BoxYAtPreferredSize)

    /** Grants its north child the width of a fixed panel and the preferred height of the child. */
    val NorthSlot =
        ParentCase(
            "BorderLayout NORTH",
            { child ->
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(NARROW, WIDE)) {
                        child(SwingModifier.north())
                    }
                }
            },
            Dimension(160, 90),
        )

    /** The parents of every kind, for content that settles at the size they grant it. */
    val StockParents: List<ParentCase> = StretchingParents + FollowingParents + NorthSlot

    /** Content that is resized while its enter runs, under a parent that grants the preferred size. */
    val ResizeCases: List<ResizeCase> =
        listOf(
            ResizeCase("growing under FlowLayout", FlowLayoutParent, NARROW, WIDE, Dimension(320, 180)),
            ResizeCase("shrinking under BoxLayout Y_AXIS", BoxYAtPreferredSize, WIDE, NARROW, Dimension(160, 90)),
        )

    val Transforms: List<TransformCase> =
        listOf(
            TransformCase("fade", fadeIn(tween(TRANSITION_MILLIS)) togetherWith fadeOut(tween(TRANSITION_MILLIS))),
            TransformCase(
                "expand and shrink",
                expandIn(tween(TRANSITION_MILLIS)) togetherWith shrinkOut(tween(TRANSITION_MILLIS)),
            ),
        )

    /** A viewport narrower than [WIDE] that scrolls both ways. */
    val InViewport: StockParent = { child ->
        Panel(PanelLayout.Flow()) {
            ScrollPane(modifier = SwingModifier.preferredSize(NARROW, NARROW + NARROW / 4)) {
                Viewport { child(SwingModifier) }
            }
        }
    }

    /** Prefers [WIDE] by 20, and keeps a 16:9 aspect ratio at the width it is offered. */
    val WideContent: TaggedContent = { modifier ->
        Label("", modifier = modifier.aspectRatio(RATIO).preferredSize(WIDE, 20))
    }

    /** Prefers the width of its text, and fills the width it is offered at a 16:9 aspect ratio. */
    val FillingContent: TaggedContent = { modifier ->
        Label("Preview", modifier = modifier.fillMaxWidth().aspectRatio(RATIO))
    }

    fun atAspect(width: Int): Dimension = Dimension(width, (width / RATIO).roundToInt())

    /** The size content that holds [RATIO] takes in [box]: the full width, or the full height where that is short. */
    fun atAspectWithin(box: Dimension): Dimension {
        val byWidth = atAspect(box.width)
        return if (byWidth.height <= box.height) byWidth else Dimension((box.height * RATIO).roundToInt(), box.height)
    }

    /** [observeFrameSizes] for an enter, which also records the extent of the viewport the container is in. */
    fun recordEntering(
        parent: StockParent,
        container: EnteringContainer,
    ): Entering {
        var viewportExtent: Dimension? = null
        val frames =
            observeFrameSizes(parent, container) { frame ->
                if (frame == 2) {
                    viewportExtent = onAllNodesOfType<JViewport>().fetchAll().firstOrNull()?.extentSize
                }
            }
        return Entering(frames, viewportExtent)
    }

    /** [recordEntering] that also asserts that the layout settles on every frame. */
    fun observeEntering(
        parent: StockParent,
        container: EnteringContainer,
    ): Entering = recordEntering(parent, container).also { assertLayoutsSettle(it.frames) }

    /**
     * The sizes of the content and its container on each frame of an enter, and on each of [frames] frames from the
     * resize, where the content is resized to [ResizeCase.to] once the enter is under way.
     */
    fun resizedFrames(
        resize: ResizeCase,
        container: (TaggedContent) -> EnteringContainer,
        frames: Int = FRAMES_TO_SETTLE,
    ): ResizedFrames {
        val width = mutableIntStateOf(resize.from)
        val entering =
            container { modifier ->
                Label("", modifier = modifier.aspectRatio(RATIO).preferredSize(width.intValue, 20))
            }
        val framesBeforeResize = TRANSITION_MILLIS / FRAME_MILLIS / 4
        val all =
            observeFrameSizes(resize.parent.parent, entering, framesBeforeResize + frames) { frame ->
                if (frame == framesBeforeResize - 1) width.intValue = resize.to
            }
        return ResizedFrames(all, all.drop(framesBeforeResize))
    }

    /**
     * The sizes at the end of each of [frames] frames from the enter. With [settle], one more entry follows: the sizes
     * once the transition has run to its end.
     */
    internal fun observeFrameSizes(
        parent: StockParent,
        container: EnteringContainer,
        frames: Int = FRAMES_TO_SETTLE,
        settle: Boolean = false,
        afterFrame: ComposeSwingTest.(Int) -> Unit = {},
    ): List<FrameSizes> {
        val sizes = mutableListOf<FrameSizes>()
        runComposeSwingTest {
            var shown by mutableStateOf(false)
            setContent {
                parent { modifier -> container(modifier.testTag(CONTAINER), shown, SwingModifier.testTag(CONTENT)) }
            }
            mainClock.autoAdvance = false
            shown = true
            var paintedSize: Dimension? = null
            repeat(frames) { frame ->
                driveOneFrame()
                val animation = onNodeWithTag(CONTAINER).fetch<JComponent>()
                val allocatedSize = Dimension(animation.size)
                val corrections = cyclesUntilStable(animation)
                val resized = allocatedSize != paintedSize
                paintedSize = Dimension(animation.size)
                sizes +=
                    FrameSizes(
                        content = onNodeWithTag(CONTENT).fetch().size,
                        container = Dimension(animation.size),
                        corrections = corrections,
                        resized = resized,
                    )
                afterFrame(frame)
            }
            if (settle) {
                mainClock.autoAdvance = true
                awaitIdle()
                val animation = onNodeWithTag(CONTAINER).fetch<JComponent>()
                sizes +=
                    FrameSizes(
                        content = onNodeWithTag(CONTENT).fetch().size,
                        container = Dimension(animation.size),
                        corrections = cyclesUntilStable(animation),
                        resized = false,
                    )
            }
        }
        return sizes
    }
}

@Composable
internal fun InitiallyExiting(
    exit: ExitTransition,
    modifier: SwingModifier,
    enter: EnterTransition = EnterTransition.None,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(true).apply { targetState = false } }
    AnimatedVisibility(state, modifier, enter, exit, content = content)
}

internal suspend fun ComposeSwingTest.assertExitDuration(
    tag: String,
    name: String,
) {
    mainClock.advanceTimeBy((EXIT_DURATION_MILLIS - 320).milliseconds)
    awaitIdle()
    assertEquals(1, onAllNodesWithTag(tag).fetchAll<JComponent>().size, "$name: the exit is still running")
    mainClock.advanceTimeBy(EXIT_DURATION_MILLIS.milliseconds)
    awaitIdle()
    onNodeWithTag(tag).assertDoesNotExist()
}

internal fun runEnteringScene(
    scene: @Composable (Boolean) -> Unit,
    frames: Int = 0,
    assertions: suspend ComposeSwingTest.() -> Unit,
) = runComposeSwingTest {
    var visible by mutableStateOf(false)
    setContent { scene(visible) }
    mainClock.autoAdvance = false
    visible = true
    repeat(frames) { driveOneFrame() }
    assertions()
}

internal const val EXIT_DURATION_MILLIS = 1000
