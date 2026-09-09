package org.jetbrains.compose.swing.samples.widgets.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope
import org.jetbrains.compose.swing.animation.DeferredAnimatedContent
import org.jetbrains.compose.swing.animation.DeferredAnimatedVisibility
import org.jetbrains.compose.swing.animation.MutableContentTransform
import org.jetbrains.compose.swing.animation.MutableTransform
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.scaleIn
import org.jetbrains.compose.swing.animation.scaleOut
import org.jetbrains.compose.swing.animation.slideOutHorizontally
import org.jetbrains.compose.swing.animation.togetherWith
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import javax.swing.SwingConstants

@OptIn(ExperimentalDeferredTransitionApi::class)
@Composable
internal fun ColumnScope.DeferredDismissCard() {
    ExampleCard("DeferredAnimatedVisibility (experimental)") {
        val state = remember { DeferredTransitionState(true) }
        var dragged by remember { mutableIntStateOf(0) }
        // The slider goes back to zero once the phase is over, never in the handler that ends it: the
        // phase is still standing there, so the transform would move the card first and the transition
        // would take it over from that value instead of from where the gesture left it.
        LaunchedEffect(state.pendingTargetState) {
            if (state.pendingTargetState == null) dragged = 0
        }
        val transform = remember { MutableTransform() }
        transform.update { fullSize ->
            val fraction = dragged / 100f
            alpha = 1f - fraction
            scale = 1f - 0.4f * fraction
            offset = Point((fullSize.width * fraction / 2).toInt(), 0)
        }

        WrappedCaption(
            "Preview a dismissal with the slider. Commit lets the exit continue from that position; Cancel " +
                "returns the order. The slider stands in for a pointer drag.",
        )
        rememberTransition(state).DeferredAnimatedVisibility(
            visible = { it },
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f) + slideOutHorizontally { it / 2 },
            mutableTransform = transform,
        ) {
            Block(text = "Transfer #7716 · Ready")
        }
        Panel {
            Label("Dragged: $dragged%")
            Slider(
                value = dragged,
                onValueChange = {
                    dragged = it
                    state.defer(false)
                },
                modifier =
                    SwingModifier
                        .preferredSize(Dimension(220, 24))
                        .accessibleName("Dismiss drag"),
            )
            Button("Commit", onClick = { state.animateTo(false) })
            Button("Cancel", onClick = { state.defer(state.targetState) })
            if (!state.targetState) {
                Button("Show again", onClick = { state.animateTo(true) })
            }
        }
    }
}

@OptIn(ExperimentalDeferredTransitionApi::class)
@Composable
internal fun ColumnScope.DeferredPageSwipeCard() {
    ExampleCard("DeferredAnimatedContent (experimental)") {
        val pages = listOf("Inbox · 3 unread", "Drafts · 1 unsent", "Archive · 12 messages")
        val state = remember { DeferredTransitionState(0) }
        var swiped by remember { mutableIntStateOf(0) }
        LaunchedEffect(state.pendingTargetState) {
            if (state.pendingTargetState == null) swiped = 0
        }

        WrappedCaption(
            "Preview a swipe between folders. Both pages follow the slider; Commit completes the " +
                "swipe and Cancel restores the current folder.",
        )
        rememberTransition(state).DeferredAnimatedContent(
            transitionSpec = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
            },
            mutableTransformSpec = {
                MutableContentTransform {
                    targetContentTransform { fullSize -> offset = Point(-fullSize.width * swiped / 100, 0) }
                    initialContentTransform { fullSize -> offset = Point(-fullSize.width * swiped / 100, 0) }
                }
            },
        ) { page ->
            Block(text = pages[page])
        }
        Panel {
            Label("Swiped: $swiped%")
            Slider(
                value = swiped,
                onValueChange = {
                    swiped = it
                    state.defer((state.targetState + 1) % pages.size)
                },
                modifier =
                    SwingModifier
                        .preferredSize(Dimension(220, 24))
                        .accessibleName("Swipe drag"),
            )
            Button("Commit swipe", onClick = { state.animateTo((state.targetState + 1) % pages.size) })
            Button("Cancel swipe", onClick = { state.defer(state.targetState) })
        }
    }
}

// The content the deferred transitions move while the slider holds their phase.
@Composable
private fun Block(text: String) {
    Label(
        text = text,
        modifier =
            SwingModifier
                .preferredSize(Dimension(260, 56))
                .background(themePanelColor())
                .opaque(true)
                .lineBorder(Color.GRAY)
                .horizontalAlignment(SwingConstants.CENTER),
    )
}
