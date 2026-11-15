// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.Crossfade
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.border.EmptyBorder

/** The parameters [CrossfadeFixture] varies. */
public data class CrossfadeParameters(
    val targetState: Boolean,
    val padding: Int,
    val durationMillis: Int,
    val label: String,
    val content: @Composable (Boolean) -> Unit,
)

/**
 * A [Crossfade] settled on a target state, showing a [Label] of that state, against a raw [JPanel] tree holding the
 * same label at its end state.
 */
public object CrossfadeFixture : ComposableFixture<CrossfadeParameters, JPanel>(
    initial =
        CrossfadeParameters(
            targetState = false,
            padding = 2,
            durationMillis = 32,
            label = "Before",
            content = BeforeContent,
        ),
) {
    override val alternatives: List<CrossfadeParameters> =
        listOf(
            initial.copy(targetState = true),
            initial.copy(padding = 8),
            initial.copy(durationMillis = 64),
            initial.copy(label = "After"),
            initial.copy(content = AfterContent),
        )

    @Composable
    override fun Content(parameters: CrossfadeParameters): Unit =
        Content(
            targetState = parameters.targetState,
            padding = parameters.padding,
            durationMillis = parameters.durationMillis,
            label = parameters.label,
            content = parameters.content,
        )

    /**
     * Composes a [Box] holding a [Crossfade] to [targetState], padded by [padding], fading over [durationMillis],
     * named [label], showing [content].
     */
    @Composable
    public fun Content(
        targetState: Boolean,
        padding: Int,
        durationMillis: Int,
        label: String,
        content: @Composable (Boolean) -> Unit,
    ) {
        Box {
            Crossfade(
                targetState = targetState,
                modifier = SwingModifier.padding(padding),
                animationSpec = tween(durationMillis),
                label = label,
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
     * Applies the padding and label text of [parameters] to [component], which is how a Swing program shows the end
     * state.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: CrossfadeParameters,
    ) {
        val crossfade = component.components.single() as JPanel
        val padding = parameters.padding
        if (crossfade.insets.top != padding) crossfade.border = EmptyBorder(padding, padding, padding, padding)
        val label = crossfade.components.single() as JLabel
        val word =
            when (parameters.content) {
                BeforeContent -> "Before"
                AfterContent -> "After"
                else -> error("Crossfade renders only its own BeforeContent and AfterContent")
            }
        label.text = contentText(word, parameters.targetState)
    }
}

/** The text of a label showing [word] and [state], which both arms use for the same content. */
private fun contentText(
    word: String,
    state: Boolean,
): String = "$word $state"

/** The content that shows the before wording. */
private val BeforeContent: @Composable (Boolean) -> Unit = { Label(contentText("Before", it)) }

/** The content that shows the after wording. */
private val AfterContent: @Composable (Boolean) -> Unit = { Label(contentText("After", it)) }
