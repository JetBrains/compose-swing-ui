package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.ContainerContent
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.assertPreferencesSettle
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JComponent
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How long every transition of these containers runs. */
private val TransitionTime: Duration = 1.seconds

/** The composition time after which a transition of these containers is part-way through. */
internal val PartWay: Duration = TransitionTime * 0.3

/**
 * The composition time at which a slide exit is read: a text leaf re-wrapping moves the slide's target, which ends the
 * exit on a spring before [PartWay].
 */
internal val SlideExitTime: Duration = 100.milliseconds

private fun <T> transitionTween() = tween<T>(TransitionTime.inWholeMilliseconds.toInt())

/** Entries grouped by the height a container around the leaf is expected to prefer. */
internal class HeightGroups<T>(
    /** The container prefers the height the leaf prefers. */
    val atWidth: Map<String, T> = emptyMap(),
    /** The leaf fills the width of the container, which prefers the height the leaf's aspect ratio takes from it. */
    val atFilledRatio: Map<String, T> = emptyMap(),
    /** The width of the leaf follows its height, so the container is expected only to settle. */
    val atOwnRatio: Map<String, T> = emptyMap(),
) {
    val all: Map<String, T> get() = atWidth + atFilledRatio + atOwnRatio

    operator fun plus(other: HeightGroups<T>): HeightGroups<T> =
        HeightGroups(atWidth + other.atWidth, atFilledRatio + other.atFilledRatio, atOwnRatio + other.atOwnRatio)

    fun <R> map(transform: (Map<String, T>) -> Map<String, R>): HeightGroups<R> =
        HeightGroups(transform(atWidth), transform(atFilledRatio), transform(atOwnRatio))
}

/** The width over the height of a leaf placed at an aspect ratio. */
private const val LEAF_RATIO = 2f

private val WidthPlacements: Map<String, ConstrainedScope.() -> SwingModifier> =
    mapOf<String, ConstrainedScope.() -> SwingModifier>(
        "leaf filling the width" to { SwingModifier.fillMaxWidth() },
        "leaf at its preferred width" to { SwingModifier },
    )

/** How a stock leaf is placed inside an animated container at an aspect ratio of the width it fills. */
private val FilledRatioPlacements: Map<String, ConstrainedScope.() -> SwingModifier> =
    mapOf<String, ConstrainedScope.() -> SwingModifier>(
        "leaf filling the width at an aspect ratio" to { SwingModifier.fillMaxWidth().aspectRatio(LEAF_RATIO) },
    )

/** How a stock leaf is placed inside an animated container at an aspect ratio alone: its width follows its height. */
private val OwnRatioPlacements: Map<String, ConstrainedScope.() -> SwingModifier> =
    mapOf<String, ConstrainedScope.() -> SwingModifier>(
        "leaf at an aspect ratio" to { SwingModifier.aspectRatio(LEAF_RATIO) },
    )

/**
 * The placements to check for containers that take their width from the leaf's height if [widthFromLeafHeight], or
 * for the others. A container takes its width from an [OwnRatioPlacements] leaf's height only where a Box holds it or
 * the transition has [finished]. While a request under a stock parent animates, the container's preferred width does
 * not follow the leaf's height, so those placements are checked with the others.
 * Under a finished AnimatedVisibility that [expands] its container they never settle, even inside a Box. This is a
 * limit of the layout. [expands] is left unset for an enter that keeps its container's size.
 */
private fun leafPlacements(
    widthFromLeafHeight: Boolean,
    insideBox: Boolean,
    finished: Boolean,
    expands: Boolean = false,
): HeightGroups<ConstrainedScope.() -> SwingModifier> =
    when {
        !(insideBox || finished) && widthFromLeafHeight -> HeightGroups()
        !(insideBox || finished) -> HeightGroups(WidthPlacements, FilledRatioPlacements, OwnRatioPlacements)
        widthFromLeafHeight && finished && expands -> HeightGroups()
        widthFromLeafHeight -> HeightGroups(atOwnRatio = OwnRatioPlacements)
        else -> HeightGroups(WidthPlacements, FilledRatioPlacements)
    }

private fun enteringContainers(
    kinds: Map<String, EnterTransition>,
    insideBox: Boolean,
    finished: Boolean,
    widthFromLeafHeight: Boolean,
    expands: Boolean = false,
): HeightGroups<ContainerContent> =
    leafPlacements(widthFromLeafHeight, insideBox, finished, expands).map { placements ->
        buildMap<String, ContainerContent> {
            for ((kind, enter) in kinds) {
                for ((placement, leafModifier) in placements) {
                    this["AnimatedVisibility ${hostOf(insideBox)}, $kind, $placement"] = { modifier, leaf ->
                        TransitionStartedOnComposition(modifier, insideBox, visibleAtStart = false, enter = enter) {
                            leaf(leafModifier())
                        }
                    }
                }
            }
        }
    }

private fun exitingContainers(
    kinds: Map<String, ExitTransition>,
    insideBox: Boolean,
    widthFromLeafHeight: Boolean,
): HeightGroups<ContainerContent> =
    leafPlacements(widthFromLeafHeight, insideBox, finished = false).map { placements ->
        buildMap<String, ContainerContent> {
            for ((kind, exit) in kinds) {
                for ((placement, leafModifier) in placements) {
                    this["AnimatedVisibility ${hostOf(insideBox)}, $kind, $placement"] = { modifier, leaf ->
                        TransitionStartedOnComposition(modifier, insideBox, visibleAtStart = true, exit = exit) {
                            leaf(leafModifier())
                        }
                    }
                }
            }
        }
    }

private fun hostOf(insideBox: Boolean): String = if (insideBox) "in a Box" else "under the parent"

@Composable
private fun TransitionStartedOnComposition(
    modifier: SwingModifier,
    insideBox: Boolean,
    visibleAtStart: Boolean,
    enter: EnterTransition = EnterTransition.None,
    exit: ExitTransition = ExitTransition.None,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(visibleAtStart).apply { targetState = !visibleAtStart } }
    if (insideBox) {
        Box(modifier = modifier) { AnimatedVisibility(state, enter = enter, exit = exit, content = content) }
    } else {
        AnimatedVisibility(state, modifier, enter = enter, exit = exit, content = content)
    }
}

internal fun sizeKeepingEnteringContainers(
    finished: Boolean,
    widthFromLeafHeight: Boolean,
): HeightGroups<ContainerContent> {
    val keepingSize =
        mapOf(
            "fadeIn" to fadeIn(transitionTween()),
            "slideInHorizontally" to slideInHorizontally(transitionTween()),
            "slideInVertically" to slideInVertically(transitionTween()),
        )
    // AnimatedVisibility accepts a scale only inside a Foundation layout.
    val scaling =
        mapOf(
            "scaleIn" to scaleIn(transitionTween()),
            "fadeIn + slideIn + scaleIn" to
                fadeIn(transitionTween()) + slideInVertically(transitionTween()) + scaleIn(transitionTween()),
        )
    return enteringContainers(keepingSize, insideBox = false, finished, widthFromLeafHeight) +
        enteringContainers(keepingSize + scaling, insideBox = true, finished, widthFromLeafHeight)
}

internal fun sizeChangingEnteringContainers(
    finished: Boolean,
    widthFromLeafHeight: Boolean,
): HeightGroups<ContainerContent> {
    val changingSize =
        mapOf(
            "expandIn" to expandIn(transitionTween()),
            "expandHorizontally" to expandHorizontally(transitionTween()),
            "expandVertically" to expandVertically(transitionTween()),
            "expandIn unclipped" to expandIn(transitionTween(), clip = false),
            "fadeIn + expandIn" to fadeIn(transitionTween()) + expandIn(transitionTween()),
        )
    return enteringContainers(changingSize, insideBox = false, finished, widthFromLeafHeight, expands = true) +
        enteringContainers(changingSize, insideBox = true, finished, widthFromLeafHeight, expands = true)
}

internal fun sizeKeepingExitingContainers(widthFromLeafHeight: Boolean): HeightGroups<ContainerContent> {
    val keepingSize =
        mapOf(
            "fadeOut" to fadeOut(transitionTween()),
            "slideOutHorizontally" to slideOutHorizontally(transitionTween()),
            "slideOutVertically" to slideOutVertically(transitionTween()),
        )
    // AnimatedVisibility accepts a scale only inside a Foundation layout.
    val scaling =
        mapOf(
            "scaleOut" to scaleOut(transitionTween()),
            "fadeOut + slideOut + scaleOut" to
                fadeOut(transitionTween()) + slideOutVertically(transitionTween()) + scaleOut(transitionTween()),
        )
    return exitingContainers(keepingSize, insideBox = false, widthFromLeafHeight) +
        exitingContainers(keepingSize + scaling, insideBox = true, widthFromLeafHeight)
}

internal fun sizeChangingExitingContainers(widthFromLeafHeight: Boolean): HeightGroups<ContainerContent> {
    val changingSize =
        mapOf(
            "shrinkOut" to shrinkOut(transitionTween()),
            "shrinkHorizontally" to shrinkHorizontally(transitionTween()),
            "shrinkVertically" to shrinkVertically(transitionTween()),
            "shrinkOut unclipped" to shrinkOut(transitionTween(), clip = false),
            "fadeOut + shrinkOut" to fadeOut(transitionTween()) + shrinkOut(transitionTween()),
        )
    return exitingContainers(changingSize, insideBox = false, widthFromLeafHeight) +
        exitingContainers(changingSize, insideBox = true, widthFromLeafHeight)
}

/**
 * Checks that a container that keeps the size of the stock child it holds prefers the height the child prefers, or
 * for a child that fills the width at an aspect ratio the height the ratio takes from that width.
 */
internal object ContainerHeightCheck {
    /** What is wrong with the heights of [container] and the stock child [leaf] it holds. */
    fun problems(
        container: JComponent,
        leaf: JComponent,
    ): List<String> =
        if (container.preferredSize.height == leaf.preferredSize.height) {
            emptyList()
        } else {
            listOf(
                "the container prefers ${container.preferredSize.height} and the content prefers " +
                    "${leaf.preferredSize.height} tall",
            )
        }

    /** What is wrong with the heights of [container] and the stock child [leaf] it holds at an aspect ratio. */
    fun problemsAtRatio(
        container: JComponent,
        leaf: JComponent,
    ): List<String> {
        val expected = (leaf.width / LEAF_RATIO).roundToInt()
        return if (container.preferredSize.height == expected) {
            emptyList()
        } else {
            listOf(
                "the container prefers ${container.preferredSize.height} and the content, ${leaf.width} wide at " +
                    "an aspect ratio, takes $expected tall",
            )
        }
    }

    /**
     * What is wrong with the heights of [container] and the stock child [leaf] it holds at an aspect ratio alone: the
     * leaf is as tall as the container prefers, or as the slot grants where that is less, and has the aspect ratio.
     */
    fun problemsAtOwnRatio(
        container: JComponent,
        leaf: JComponent,
    ): List<String> {
        val expectedHeight = minOf(container.preferredSize.height, container.height)
        return buildList {
            if (leaf.height != expectedHeight) {
                add("the container prefers ${container.preferredSize.height} and the content is ${leaf.height} tall")
            }
            if (leaf.height != (leaf.width / LEAF_RATIO).roundToInt()) {
                add("the content is ${leaf.width} wide and ${leaf.height} tall, off the aspect ratio")
            }
        }
    }
}

/**
 * [assertPreferencesSettle] for each group of [containers], and for [widthFromLeafHeight], each with the
 * [ContainerHeightCheck] its leaf placement needs.
 */
internal fun assertPreferencesSettleAtTheLeafsHeight(
    containers: HeightGroups<ContainerContent>,
    transitionTime: Duration? = null,
    widthFromLeafHeight: Map<String, ContainerContent> = emptyMap(),
) {
    assertPreferencesSettle(
        containers.atWidth,
        transitionTime = transitionTime,
        sizeProblems = ContainerHeightCheck::problems,
    )
    assertPreferencesSettle(
        containers.atFilledRatio,
        transitionTime = transitionTime,
        sizeProblems = ContainerHeightCheck::problemsAtRatio,
    )
    assertPreferencesSettle(
        containers.atOwnRatio,
        transitionTime = transitionTime,
        sizeProblems = ContainerHeightCheck::problemsAtOwnRatio,
        widthFromLeafHeight = widthFromLeafHeight,
    )
}

internal fun contentContainers(
    leafEnters: Boolean,
    insideBox: Boolean,
    finished: Boolean,
    widthFromLeafHeight: Boolean,
): Map<String, ContainerContent> {
    val contentKinds =
        mapOf(
            "fade" to (fadeIn(transitionTween()) to fadeOut(transitionTween())),
            "slide" to (slideInHorizontally(transitionTween()) to slideOutHorizontally(transitionTween())),
            "scale" to (scaleIn(transitionTween()) to scaleOut(transitionTween())),
            "expand and shrink" to (expandIn(transitionTween()) to shrinkOut(transitionTween())),
        )
    val sizeTransforms =
        mapOf<String, SizeTransform?>(
            "clipped size transform" to SizeTransform(clip = true) { _, _ -> transitionTween() },
            "unclipped size transform" to SizeTransform(clip = false) { _, _ -> transitionTween() },
            "no size transform" to null,
        )
    return buildMap<String, ContainerContent> {
        for ((kind, transitions) in contentKinds) {
            for ((sizing, sizeTransform) in sizeTransforms) {
                val transitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
                    (transitions.first togetherWith transitions.second) using sizeTransform
                }
                for ((placement, leafModifier) in leafPlacements(widthFromLeafHeight, insideBox, finished).all) {
                    this["AnimatedContent ${hostOf(insideBox)}, $kind, $sizing, $placement"] =
                        if (insideBox) {
                            { modifier, leaf ->
                                Box(modifier = modifier) {
                                    val state = rememberGrown()
                                    AnimatedContent(targetState = state, transitionSpec = transitionSpec) { target ->
                                        if (target == leafEnters) leaf(leafModifier()) else Label("Other content")
                                    }
                                }
                            }
                        } else {
                            { modifier, leaf ->
                                val state = remember { MutableTransitionState(false).apply { targetState = true } }
                                rememberTransition(state).AnimatedContent(modifier, transitionSpec) { target ->
                                    if (target == leafEnters) leaf(leafModifier()) else Label("Other content")
                                }
                            }
                        }
                }
            }
        }
    }
}

/** The containers of an [AnimatedContent] inside a Box under a stock parent that changes from a label to the leaf. */
internal val ContentEnteringInBoxContainers: Map<String, ContainerContent> =
    contentContainers(leafEnters = true, insideBox = true, finished = false, widthFromLeafHeight = false)

/** The [ContentEnteringInBoxContainers] that take their width from the leaf's height. */
internal val RatioContentEnteringInBoxContainers: Map<String, ContainerContent> =
    contentContainers(leafEnters = true, insideBox = true, finished = false, widthFromLeafHeight = true)

/** Containers with [animateContentSize] around a Column holding the leaf at a width. */
internal val SizeAnimatingContainers: Map<String, ContainerContent> = sizeAnimatingContainers(WidthPlacements)

/**
 * Containers with [animateContentSize] around a Column holding the leaf at an aspect ratio, which takes its width
 * from the leaf's height.
 */
internal val RatioSizeAnimatingContainers: Map<String, ContainerContent> =
    sizeAnimatingContainers(FilledRatioPlacements + OwnRatioPlacements)

/**
 * Containers with [animateContentSize] around a Column holding the leaf placed by each of [placements], which grows by
 * a label. The slot's modifier goes on an outer Box: a layout modifier is declared only under a Foundation parent.
 */
private fun sizeAnimatingContainers(
    placements: Map<String, ConstrainedScope.() -> SwingModifier>,
): Map<String, ContainerContent> =
    buildMap<String, ContainerContent> {
        for ((placement, leafModifier) in placements) {
            this["Box with animateContentSize, $placement"] = { modifier, leaf ->
                Box(modifier = modifier) {
                    val grown = rememberGrown()
                    Box(modifier = SwingModifier.animateContentSize(transitionTween())) {
                        Column {
                            leaf(leafModifier())
                            if (grown) Label("Added")
                        }
                    }
                }
            }
            this["Column with animateContentSize, $placement"] = { modifier, leaf ->
                Box(modifier = modifier) {
                    val grown = rememberGrown()
                    Column(modifier = SwingModifier.animateContentSize(transitionTween())) {
                        leaf(leafModifier())
                        if (grown) Label("Added")
                    }
                }
            }
        }
    }

@Composable
private fun rememberGrown(): Boolean {
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    return grown
}
