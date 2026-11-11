// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a container standing in a `Row` or a `Column` animates: the scope's defaults expand and shrink along
 * that layout's own axis alone, and leave the cross axis where it stands. The layout reports the target size
 * as its preferred size, so what moves is the room the layout gives the container among its siblings.
 */
class AnimatedVisibilityInLayoutTest {
    enum class LayoutAxis(
        val withVisible: @Composable (visible: Boolean) -> Unit,
        val withState: @Composable (state: MutableTransitionState<Boolean>) -> Unit,
        val along: (Dimension) -> Int,
        val across: (Dimension) -> Int,
        val crossExtent: Int,
    ) {
        InsideARow(
            withVisible = { InRow { AnimatedVisibility(visible = it) { Part() } } },
            withState = { InRow { AnimatedVisibility(visibleState = it) { Part() } } },
            along = { it.width },
            across = { it.height },
            crossExtent = PART_HEIGHT,
        ),
        InsideAColumn(
            withVisible = { InColumn { AnimatedVisibility(visible = it) { Part() } } },
            withState = { InColumn { AnimatedVisibility(visibleState = it) { Part() } } },
            along = { it.height },
            across = { it.width },
            crossExtent = PART_WIDTH,
        ),
    }

    /**
     * A transition of the container driven through one of its two overloads, sampled [settlingFrames] frames in.
     * Where [direction] is 1 the layout's axis expands, and where it is -1 it shrinks.
     */
    enum class Motion(
        val settlingFrames: Int,
        val direction: Int,
    ) {
        EnterOfVisible(2, 1) {
            override fun ComposeSwingTest.begin(axis: LayoutAxis) {
                var visible by mutableStateOf(false)
                setContent { axis.withVisible(visible) }
                mainClock.autoAdvance = false
                visible = true
            }
        },
        ExitOfVisible(0, -1) {
            override fun ComposeSwingTest.begin(axis: LayoutAxis) {
                var visible by mutableStateOf(true)
                setContent { axis.withVisible(visible) }
                mainClock.autoAdvance = false
                visible = false
            }
        },
        EnterOfState(2, 1) {
            override fun ComposeSwingTest.begin(axis: LayoutAxis) {
                mainClock.autoAdvance = false
                setContent { axis.withState(remember { enteringOnMount() }) }
            }
        },
        ExitOfState(0, -1) {
            override fun ComposeSwingTest.begin(axis: LayoutAxis) {
                val state = MutableTransitionState(true)
                setContent { axis.withState(state) }
                mainClock.autoAdvance = false
                state.targetState = false
            }
        }, ;

        /** Mounts the container in [axis]'s layout and starts the transition with the clock held. */
        abstract fun ComposeSwingTest.begin(axis: LayoutAxis)
    }

    @ParameterizedTest(name = "{1} {0}")
    @MethodSource("axesAndMotions")
    fun `a default transition moves along the layout's axis and holds the cross axis`(
        axis: LayoutAxis,
        motion: Motion,
    ) = runComposeSwingTest {
        with(motion) { begin(axis) }
        val slots = slotsWhileAnimating(settlingFrames = motion.settlingFrames)
        assertEquals(listOf(axis.crossExtent), slots.map(axis.across).distinct(), "the cross axis moved: $slots")
        assertTrue(
            motion.direction * (axis.along(slots.last()) - axis.along(slots.first())) > 0,
            "the layout's axis did not move the way the $motion does: $slots",
        )
    }

    /**
     * The room the layout gives the container over five frames of a transition, sampled [settlingFrames] frames in:
     * along the layout's axis, how far the sibling after the container stands from the one before it, and across it,
     * the extent the container is laid out at.
     */
    private fun ComposeSwingTest.slotsWhileAnimating(settlingFrames: Int): List<Dimension> {
        repeat(settlingFrames) { mainClock.advanceTimeByFrame() }
        return List(5) {
            mainClock.advanceTimeByFrame()
            val first = onNodeWithText("first").fetch()
            val last = onNodeWithText("last").fetch()
            val container = onNodeWithText("body").fetch().parent
            if (first.y == last.y) {
                Dimension(last.x - first.x - first.width, container.height)
            } else {
                Dimension(container.width, last.y - first.y - first.height)
            }
        }
    }

    private companion object {
        @JvmStatic
        fun axesAndMotions(): List<Arguments> =
            LayoutAxis.entries.flatMap { axis -> Motion.entries.map { arguments(axis, it) } }
    }
}

/** A row holding the container between two siblings, whose positions show the room the container is given. */
@Composable
private fun InRow(container: @Composable RowScope.() -> Unit) =
    Row {
        Label(text = "first", style = { preferredSize(width = 40, height = 10) })
        container()
        Label(text = "last", style = { preferredSize(width = 40, height = 10) })
    }

/** A column holding the container between two siblings; see [InRow]. */
@Composable
private fun InColumn(container: @Composable ColumnScope.() -> Unit) =
    Column {
        Label(text = "first", style = { preferredSize(width = 10, height = 40) })
        container()
        Label(text = "last", style = { preferredSize(width = 10, height = 40) })
    }

/** The content the layouts animate, of a size neither sibling has. */
@Composable
private fun Part() = Label(text = "body", style = { preferredSize(width = PART_WIDTH, height = PART_HEIGHT) })

private const val PART_WIDTH = 80
private const val PART_HEIGHT = 40

/** A transition state that is invisible where it is built and enters from there on the pass that mounts it. */
private fun enteringOnMount() = MutableTransitionState(false).apply { targetState = true }
