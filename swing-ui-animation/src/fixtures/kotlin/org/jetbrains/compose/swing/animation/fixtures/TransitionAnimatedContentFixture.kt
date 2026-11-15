// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.AnimatedContent
import org.jetbrains.compose.swing.animation.AnimatedContentScope
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope
import org.jetbrains.compose.swing.animation.ContentTransform
import org.jetbrains.compose.swing.animation.EnterExitState
import org.jetbrains.compose.swing.animation.core.animateInt
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.togetherWith
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.border.EmptyBorder

/** The parameters [TransitionAnimatedContentFixture] varies. */
public data class TransitionAnimatedContentParameters(
    val inputTargetState: Boolean,
    val transitionLabel: String,
    val padding: Int,
    val durationMillis: Int,
    val contentAlignment: Alignment,
    val contentKey: (Boolean) -> Any?,
    val content: @Composable AnimatedContentScope.(Boolean) -> Unit,
)

/**
 * A transition-driven [AnimatedContent] inside a [Box], showing a [Label] of its current content, against a raw
 * [JPanel] tree holding the same label at its end state.
 */
public object TransitionAnimatedContentFixture :
    ComposableFixture<TransitionAnimatedContentParameters, JPanel>(
        initial =
            TransitionAnimatedContentParameters(
                inputTargetState = false,
                transitionLabel = "Before",
                padding = 2,
                durationMillis = 32,
                contentAlignment = Alignment.TopStart,
                contentKey = { it },
                content = BeforeContent,
            ),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TransitionAnimatedContentParameters> =
        listOf(
            initial.copy(inputTargetState = true),
            initial.copy(transitionLabel = "After"),
            initial.copy(padding = 8),
            initial.copy(durationMillis = 64),
            initial.copy(contentAlignment = Alignment.Center),
            initial.copy(contentKey = { "Key $it" }),
            initial.copy(content = AfterContent),
        )

    @Composable
    override fun Content(parameters: TransitionAnimatedContentParameters): Unit =
        Content(
            inputTargetState = parameters.inputTargetState,
            transitionLabel = parameters.transitionLabel,
            padding = parameters.padding,
            durationMillis = parameters.durationMillis,
            contentAlignment = parameters.contentAlignment,
            contentKey = parameters.contentKey,
            content = parameters.content,
        )

    /**
     * Composes a [Box] holding an [AnimatedContent] on a transition named [transitionLabel] and fed
     * [inputTargetState], padded by [padding], whose transition lasts [durationMillis], aligned by
     * [contentAlignment], keyed by [contentKey], and showing [content].
     */
    @Composable
    public fun Content(
        inputTargetState: Boolean,
        transitionLabel: String,
        padding: Int,
        durationMillis: Int,
        contentAlignment: Alignment,
        contentKey: (Boolean) -> Any?,
        content: @Composable AnimatedContentScope.(Boolean) -> Unit,
    ) {
        val spec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
            (fadeIn(tween(durationMillis)) togetherWith fadeOut(tween(durationMillis))).using(null)
        }
        Box {
            val transition = updateTransition(inputTargetState, label = transitionLabel)
            transition.AnimatedContent(
                modifier = SwingModifier.padding(padding),
                transitionSpec = spec,
                contentAlignment = contentAlignment,
                contentKey = contentKey,
                content = content,
            )
        }
    }

    /** Builds a raw panel tree holding one bare [JLabel], before any of the fixture's values are applied. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            alignmentX = 0f
            alignmentY = 0f
            layout = OverlayLayout(this)
            add(
                JPanel().apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    layout = OverlayLayout(this)
                    add(JLabel())
                },
            )
        }

    /**
     * Applies the padding, alignment and label text of [parameters] to [component], which is how a Swing program
     * shows the same label at its end state. The transition name changes only how the transition is labeled.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: TransitionAnimatedContentParameters,
    ) {
        val padding = parameters.padding
        if (component.insets.top != padding) component.border = EmptyBorder(padding, padding, padding, padding)
        val panel = component.components.single() as JPanel
        val label = panel.components.single() as JLabel
        val alignment = if (parameters.contentAlignment == Alignment.Center) 0.5f else 0f
        if (label.alignmentX != alignment) {
            label.alignmentX = alignment
            label.alignmentY = alignment
            label.revalidate()
        }
        label.text =
            when (parameters.content) {
                BeforeContent -> "Before ${parameters.inputTargetState}: $BEFORE_SHOWN"
                AfterContent -> "After ${parameters.inputTargetState}: $AFTER_SHOWN"
                else -> error("TransitionAnimatedContent renders only its own BeforeContent and AfterContent")
            }
    }
}

/** The value the before label shows once its content is visible. */
private const val BEFORE_SHOWN = 80

/** The value the after label shows once its content is visible. */
private const val AFTER_SHOWN = 60

/**
 * Composes a label of [word], [state] and an animated value that is [shown] while the content is visible,
 * else [hidden].
 */
@Composable
private fun AnimatedContentScope.AnimatedLabel(
    word: String,
    state: Boolean,
    shown: Int,
    hidden: Int,
) {
    val value =
        transition.animateInt(transitionSpec = { tween(32) }) {
            if (it == EnterExitState.Visible) shown else hidden
        }
    Label("$word $state: ${value.value}")
}

/** The content that shows the before label. */
private val BeforeContent: @Composable AnimatedContentScope.(Boolean) -> Unit = {
    AnimatedLabel("Before", it, BEFORE_SHOWN, 20)
}

/** The content that shows the after label. */
private val AfterContent: @Composable AnimatedContentScope.(Boolean) -> Unit = {
    AnimatedLabel("After", it, AFTER_SHOWN, 10)
}
