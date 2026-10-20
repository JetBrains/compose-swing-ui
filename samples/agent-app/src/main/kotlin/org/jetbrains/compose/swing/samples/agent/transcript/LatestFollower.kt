package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import org.jetbrains.compose.swing.animation.animateScrollTo
import org.jetbrains.compose.swing.components.layout.ScrollState

/**
 * Where the reader of [scroll] stands: following the newest content, or scrolled up to read something earlier.
 * Scrolling up stops following; coming back down, or [jumpToLatest], resumes it.
 */
@Stable
internal class LatestFollower(
    private val scroll: ScrollState,
    initiallyFollowing: Boolean = true,
) {
    var isFollowing by mutableStateOf(initiallyFollowing)
        private set

    private var lastY = scroll.y

    /** Whether to offer the way back down: the reader left the bottom and there is more below. */
    val showsJump: Boolean get() = !isFollowing && scroll.canScrollForwardY

    fun jumpToLatest() {
        isFollowing = true
    }

    /**
     * Takes a move up as the reader's intent to leave the bottom, and reaching the bottom as the intent to follow.
     * Content growing below never moves the position up, and neither does a glide down to it.
     */
    fun onPositionChange(y: Int) {
        if (y == lastY) return
        when {
            y < lastY -> isFollowing = false
            y >= scroll.maxY -> isFollowing = true
        }
        lastY = y
    }

    /** Brings the bottom at [maxY] into view: the first layout lands there at once, later moves glide. */
    suspend fun follow(
        maxY: Int,
        isFirstLayout: Boolean,
    ) {
        if (isFirstLayout) {
            scroll.y = maxY
            return
        }
        try {
            scroll.animateScrollTo(y = maxY)
        } catch (e: CancellationException) {
            // The pane ends the glide when the reader moves it, while this coroutine stays active; a new bottom
            // cancels this coroutine instead, and following goes on.
            if (currentCoroutineContext().isActive) isFollowing = false
            throw e
        }
    }
}

@Composable
internal fun rememberLatestFollower(
    scroll: ScrollState,
    isSettled: Boolean,
): LatestFollower {
    val follower =
        rememberSaveable(
            scroll,
            saver =
                Saver<LatestFollower, Boolean>(
                    save = { it.isFollowing },
                    restore = { LatestFollower(scroll, it) },
                ),
        ) { LatestFollower(scroll) }
    LaunchedEffect(follower, isSettled) {
        if (!isSettled) return@LaunchedEffect
        snapshotFlow { scroll.y }.filter { scroll.maxY > 0 }.collect(follower::onPositionChange)
    }
    LaunchedEffect(follower, isSettled) {
        if (!isSettled) return@LaunchedEffect
        var isFirstLayout = true
        snapshotFlow { follower.isFollowing to scroll.maxY }
            .filter { (_, maxY) -> maxY > 0 }
            .collectLatest { (isFollowing, maxY) ->
                val first = isFirstLayout
                isFirstLayout = false
                if (isFollowing) follower.follow(maxY, isFirstLayout = first)
            }
    }
    return follower
}
