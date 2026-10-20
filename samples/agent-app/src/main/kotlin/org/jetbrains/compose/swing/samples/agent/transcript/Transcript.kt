package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.EnterTransition
import org.jetbrains.compose.swing.animation.ExitTransition
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.slideInVertically
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.ScrollState
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxSize
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.widthIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.samples.agent.state.PermissionDecision
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.state.Turn
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Scrollable
import javax.swing.UIManager

/**
 * The conversation of one session: each turn's prompt, then what the agent did and wrote for it. It follows new
 * content while the reader stays at the bottom, and offers a way back down once they scroll up.
 */
@Composable
internal fun ConstrainedScope.Transcript(
    turns: List<Turn>,
    onPermissionDecision: (TranscriptItem.Permission, PermissionDecision) -> Unit,
    modifier: SwingModifier = SwingModifier,
    isSettled: Boolean = true,
) {
    val colors = LocalAgentTheme.current.colors
    val scroll = rememberTranscriptScrollState(isSettled)
    val follower = rememberLatestFollower(scroll, isSettled)
    // What the session held when it was first shown is history: it is there already, so it must not animate in.
    val history = remember(turns) { turns.flatMapTo(HashSet<Any>()) { it.items + it } }
    // The scrollbar always takes its width from the trailing side; the same width on the leading side keeps the
    // column centered in the pane, where the composer below is centered.
    val gutter = remember { UIManager.getInt("ScrollBar.width") }
    Box(modifier) {
        ScrollPane(
            SwingModifier.fillMaxSize().border(BorderFactory.createEmptyBorder()).testTag("transcript"),
            state = scroll,
            verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
        ) {
            Viewport(unitIncrement = 16) {
                SwingNode(
                    factory = { TranscriptPanel() },
                    content = {
                        Column(
                            SwingModifier.background(colors.surface, RectangleShape),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                SwingModifier
                                    .padding(start = gutter)
                                    .padding(24)
                                    .widthIn(max = 760)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16),
                            ) {
                                if (turns.isEmpty()) EmptyTranscript(SwingModifier.fillMaxWidth())
                                for (turn in turns) {
                                    key(turn) {
                                        TurnView(
                                            turn = turn,
                                            history = history,
                                            onPermissionDecision = onPermissionDecision,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
        if (follower.showsJump) {
            JumpToLatestPill(
                onClick = follower::jumpToLatest,
                modifier = SwingModifier.align(Alignment.BottomCenter).padding(bottom = 16),
            )
        }
    }
}

/**
 * One turn: the prompt, then each item as it arrives, then a typing indicator while the agent works. Only what is
 * not in [history] animates in.
 */
@Composable
private fun ColumnScope.TurnView(
    turn: Turn,
    history: Set<Any>,
    onPermissionDecision: (TranscriptItem.Permission, PermissionDecision) -> Unit,
) {
    FadeInOnAppear(animate = turn !in history) {
        UserBubble(turn.prompt, SwingModifier.align(Alignment.End))
    }
    for (item in turn.items) {
        key(item) {
            FadeInOnAppear(animate = item !in history) {
                TranscriptItemView(item, onPermissionDecision)
            }
        }
    }
    AnimatedVisibility(visible = turn.isRunning && turn.showsWork(), label = "typing") {
        TypingIndicator()
    }
}

/**
 * Whether the agent works with nothing else showing it: not while text streams or a thought shimmers, and not
 * while the turn waits for the user on a permission card.
 */
private fun Turn.showsWork(): Boolean =
    when (val last = items.lastOrNull()) {
        is TranscriptItem.AssistantText -> false
        is TranscriptItem.Thinking -> last.isDone
        is TranscriptItem.Permission -> last.decision != null
        else -> true
    }

@Composable
private fun ConstrainedScope.TranscriptItemView(
    item: TranscriptItem,
    onPermissionDecision: (TranscriptItem.Permission, PermissionDecision) -> Unit,
) {
    when (item) {
        is TranscriptItem.AssistantText -> {
            MarkdownView(markdown = item.text, modifier = SwingModifier.fillMaxWidth())
        }

        is TranscriptItem.Thinking -> {
            ThinkingRow(item.text, item.isDone, SwingModifier.fillMaxWidth())
        }

        is TranscriptItem.ToolCall -> {
            ToolCallCard(item, SwingModifier.fillMaxWidth())
        }

        is TranscriptItem.Permission -> {
            PermissionCard(
                permission = item,
                onDecision = { onPermissionDecision(item, it) },
                modifier = SwingModifier.fillMaxWidth(),
            )
        }

        is TranscriptItem.Failure -> {
            ErrorCard(item, SwingModifier.fillMaxWidth())
        }

        is TranscriptItem.Usage -> {
            UsageFooter(item.inputTokens, item.outputTokens)
        }
    }
}

/**
 * Shows [content] fading in and rising a little on its first composition, unless [animate] is `false`. The
 * transition state starts hidden and targets shown at once, which is what makes a fresh item animate in.
 */
@Composable
private fun ColumnScope.FadeInOnAppear(
    animate: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(!animate).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibility,
        modifier = SwingModifier.fillMaxWidth(),
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        label = "entering",
    ) {
        Column(
            SwingModifier.fillMaxWidth().animateEnterExit(
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = ExitTransition.None,
                label = "entering content",
            ),
        ) {
            content()
        }
    }
}

private class TranscriptPanel :
    JPanel(BorderLayout()),
    Scrollable {
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = preferredSize.height < (parent?.height ?: 0)

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = 16

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = visibleRect.height
}

@Composable
private fun rememberTranscriptScrollState(isSettled: Boolean): ScrollState {
    var position by rememberSaveable { mutableStateOf(listOf(0, 0)) }
    val scroll = rememberScrollState(position[0], position[1])
    // A fading session has a temporary viewport; only its settled layout owns the saved reading position.
    LaunchedEffect(scroll, isSettled) {
        if (!isSettled) return@LaunchedEffect
        scroll.x = position[0]
        scroll.y = position[1]
        snapshotFlow { scroll.x to scroll.y }.collect { (x, y) ->
            if (scroll.viewHeight > 0) position = listOf(x, y)
        }
    }
    return scroll
}
