// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.animation.AnimatedContentScope
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope
import org.jetbrains.compose.swing.animation.ContentTransform
import org.jetbrains.compose.swing.animation.DeferredAnimatedContent
import org.jetbrains.compose.swing.animation.EnterExitState
import org.jetbrains.compose.swing.animation.MutableContentTransform
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.animateInt
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.togetherWith
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.border.EmptyBorder

/** The parameters [DeferredAnimatedContentFixture] varies. */
@OptIn(ExperimentalDeferredTransitionApi::class)
public data class DeferredAnimatedContentParameters(
    val padding: Int,
    val durationMillis: Int,
    val contentAlignment: Alignment,
    val contentKey: (Boolean) -> Any?,
    val mutableTransformSpec: AnimatedContentTransitionScope<Boolean>.() -> MutableContentTransform?,
    val content: @Composable AnimatedContentScope.(Boolean) -> Unit,
)

/**
 * A deferred [DeferredAnimatedContent] over a settled state, showing a [Label] of its current content, against a raw
 * [JPanel] tree holding the same label at its end state.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
public object DeferredAnimatedContentFixture : ComposableFixture<DeferredAnimatedContentParameters, JPanel>(
    initial =
        DeferredAnimatedContentParameters(
            padding = 2,
            durationMillis = 32,
            contentAlignment = Alignment.TopStart,
            contentKey = { it },
            mutableTransformSpec = { MutableContentTransform { targetContentTransform { alpha = 0.5f } } },
            content = BeforeContent,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<DeferredAnimatedContentParameters> =
        listOf(
            initial.copy(padding = 8),
            initial.copy(durationMillis = 64),
            initial.copy(contentAlignment = Alignment.Center),
            initial.copy(contentKey = { "Key $it" }),
            initial.copy(
                mutableTransformSpec = { MutableContentTransform { targetContentTransform { alpha = 0.75f } } },
            ),
            initial.copy(content = AfterContent),
        )

    @Composable
    override fun Content(parameters: DeferredAnimatedContentParameters): Unit =
        Content(
            padding = parameters.padding,
            durationMillis = parameters.durationMillis,
            contentAlignment = parameters.contentAlignment,
            contentKey = parameters.contentKey,
            mutableTransformSpec = parameters.mutableTransformSpec,
            content = parameters.content,
        )

    /**
     * Composes a [Box] holding a deferred [DeferredAnimatedContent] padded by [padding], whose transition lasts
     * [durationMillis], aligned by [contentAlignment], keyed by [contentKey], whose deferred phase transforms the
     * contents as [mutableTransformSpec] declares, and showing [content].
     */
    @Composable
    public fun Content(
        padding: Int,
        durationMillis: Int,
        contentAlignment: Alignment,
        contentKey: (Boolean) -> Any?,
        mutableTransformSpec: AnimatedContentTransitionScope<Boolean>.() -> MutableContentTransform?,
        content: @Composable AnimatedContentScope.(Boolean) -> Unit,
    ) {
        val state = remember { DeferredTransitionState(false) }
        val transition = rememberTransition(state)
        val spec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
            (fadeIn(tween(durationMillis)) togetherWith fadeOut(tween(durationMillis))).using(null)
        }
        Box {
            transition.DeferredAnimatedContent(
                modifier = SwingModifier.padding(padding),
                transitionSpec = spec,
                contentAlignment = contentAlignment,
                contentKey = contentKey,
                mutableTransformSpec = mutableTransformSpec,
                content = content,
            )
        }
    }

    /** Builds a raw panel tree holding one bare [JLabel], before any of the fixture's values are applied. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            layout = BorderLayout()
            add(
                JPanel().apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    layout = OverlayLayout(this)
                    add(JLabel())
                },
                BorderLayout.CENTER,
            )
        }

    /**
     * Sets the padding of [parameters] as an empty border on [component], and applies the alignment and
     * label text to the label, which is how a Swing program shows the same label at its end state.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: DeferredAnimatedContentParameters,
    ) {
        if (component.insets.top != parameters.padding) {
            val padding = parameters.padding
            component.border = EmptyBorder(padding, padding, padding, padding)
            component.revalidate()
        }
        val panel = component.components.single() as JPanel
        val label = panel.components.single() as JLabel
        val alignment = if (parameters.contentAlignment == Alignment.Center) 0.5f else 0f
        if (label.alignmentX != alignment) {
            label.alignmentX = alignment
            label.alignmentY = alignment
            panel.revalidate()
        }
        label.text =
            when (parameters.content) {
                BeforeContent -> "Before false: $BEFORE_SHOWN"
                AfterContent -> "After false: $AFTER_SHOWN"
                else -> error("DeferredAnimatedContent renders only its own BeforeContent and AfterContent")
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
