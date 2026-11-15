// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.animation.AnimatedVisibilityScope
import org.jetbrains.compose.swing.animation.DeferredAnimatedVisibility
import org.jetbrains.compose.swing.animation.MutableTransform
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.border.EmptyBorder

/** The parameters [DeferredAnimatedVisibilityFixture] varies. */
public data class DeferredAnimatedVisibilityParameters(
    val visibleAbove: Int,
    val modifierPadding: Int,
    val enterDurationMillis: Int,
    val exitDurationMillis: Int,
    val transformAlpha: Float,
    val content: @Composable AnimatedVisibilityScope.() -> Unit,
)

/**
 * A [DeferredAnimatedVisibility] over a deferred transition settled at its target state, against a raw [JPanel] that
 * shows the same content at that state.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
public object DeferredAnimatedVisibilityFixture : ComposableFixture<DeferredAnimatedVisibilityParameters, JPanel>(
    initial =
        DeferredAnimatedVisibilityParameters(
            visibleAbove = 50,
            modifierPadding = 2,
            enterDurationMillis = 32,
            exitDurationMillis = 32,
            transformAlpha = 0.5f,
            content = BeforeContent,
        ),
) {
    override val alternatives: List<DeferredAnimatedVisibilityParameters> =
        listOf(
            initial.copy(visibleAbove = 40),
            initial.copy(modifierPadding = 8),
            initial.copy(enterDurationMillis = 64),
            initial.copy(exitDurationMillis = 64),
            initial.copy(transformAlpha = 0.75f),
            initial.copy(content = AfterContent),
        )

    @Composable
    override fun Content(parameters: DeferredAnimatedVisibilityParameters): Unit =
        Content(
            visibleAbove = parameters.visibleAbove,
            modifierPadding = parameters.modifierPadding,
            enterDurationMillis = parameters.enterDurationMillis,
            exitDurationMillis = parameters.exitDurationMillis,
            transformAlpha = parameters.transformAlpha,
            content = parameters.content,
        )

    /**
     * Composes a [Box] around a [DeferredAnimatedVisibility] over a deferred transition settled at its target state,
     * with these values for its visibility, padding, durations, transform and content.
     */
    @Composable
    public fun Content(
        visibleAbove: Int,
        modifierPadding: Int,
        enterDurationMillis: Int,
        exitDurationMillis: Int,
        transformAlpha: Float,
        content: @Composable AnimatedVisibilityScope.() -> Unit,
    ) {
        val transitionState = remember { DeferredTransitionState(TARGET_STATE) }
        val transition = rememberTransition(transitionState)
        val transform = remember(transformAlpha) { MutableTransform().apply { update { alpha = transformAlpha } } }
        Box {
            transition.DeferredAnimatedVisibility(
                visible = { it > visibleAbove },
                modifier = SwingModifier.padding(modifierPadding),
                enter = fadeIn(tween(enterDurationMillis)),
                exit = fadeOut(tween(exitDurationMillis)),
                mutableTransform = transform,
                content = content,
            )
        }
    }

    /** Builds the raw panel with a container panel holding a label, before any value is applied. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            isOpaque = false
            layout = OverlayLayout(this)
            add(newContainer())
        }

    /**
     * Applies the end state of [parameters] to [component]: the container is present while visible and gone while
     * hidden, as the composed side emits no node for it; its padding is a border and its label shows the text of the
     * content. The durations and the transform shape only the way the end state is reached, so they set nothing.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: DeferredAnimatedVisibilityParameters,
    ) {
        val existing = component.components.filterIsInstance<JPanel>().singleOrNull()
        if (TARGET_STATE > parameters.visibleAbove) {
            val container = existing ?: newContainer().also { component.add(it) }
            val padding = parameters.modifierPadding
            if (container.insets.top != padding) container.border = EmptyBorder(padding, padding, padding, padding)
            val label = container.components.filterIsInstance<JLabel>().single()
            label.text =
                when (parameters.content) {
                    BeforeContent -> BEFORE_TEXT
                    AfterContent -> AFTER_TEXT
                    else -> error("DeferredAnimatedVisibility renders only its own BeforeContent and AfterContent")
                }
            if (existing == null) component.revalidate()
        } else if (existing != null) {
            component.remove(existing)
            component.revalidate()
        }
    }

    private fun newContainer(): JPanel =
        JPanel().apply {
            isOpaque = false
            layout = OverlayLayout(this)
            add(JLabel())
        }

    /** The state the deferred transition is settled at. */
    private const val TARGET_STATE = 80
}

/** The label text shown while the content before the swap is composed. */
private const val BEFORE_TEXT = "Before: 80"

/** The label text shown while the content after the swap is composed. */
private const val AFTER_TEXT = "After: 60"

/** The content that shows the before text. */
private val BeforeContent: @Composable AnimatedVisibilityScope.() -> Unit = { Label(BEFORE_TEXT) }

/** The content that shows the after text. */
private val AfterContent: @Composable AnimatedVisibilityScope.() -> Unit = { Label(AFTER_TEXT) }
