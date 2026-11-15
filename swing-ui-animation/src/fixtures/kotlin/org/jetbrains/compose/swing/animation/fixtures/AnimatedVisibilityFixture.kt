// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.AnimatedVisibilityScope
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.border.EmptyBorder

/** The parameters [AnimatedVisibilityFixture] varies. */
public data class AnimatedVisibilityParameters(
    val visible: Boolean,
    val modifierPadding: Int,
    val enterDurationMillis: Int,
    val exitDurationMillis: Int,
    val label: String,
    val content: @Composable AnimatedVisibilityScope.() -> Unit,
)

/**
 * An [AnimatedVisibility] driven by a [Boolean], settled at its end state, against a raw [JPanel] showing the same
 * content.
 */
public object AnimatedVisibilityFixture : ComposableFixture<AnimatedVisibilityParameters, JPanel>(
    initial =
        AnimatedVisibilityParameters(
            visible = true,
            modifierPadding = 2,
            enterDurationMillis = 32,
            exitDurationMillis = 32,
            label = "Before",
            content = BeforeContent,
        ),
) {
    override val alternatives: List<AnimatedVisibilityParameters> =
        listOf(
            initial.copy(visible = false),
            initial.copy(modifierPadding = 8),
            initial.copy(enterDurationMillis = 64),
            initial.copy(exitDurationMillis = 64),
            initial.copy(label = "After"),
            initial.copy(content = AfterContent),
        )

    @Composable
    override fun Content(parameters: AnimatedVisibilityParameters): Unit =
        Content(
            visible = parameters.visible,
            modifierPadding = parameters.modifierPadding,
            enterDurationMillis = parameters.enterDurationMillis,
            exitDurationMillis = parameters.exitDurationMillis,
            label = parameters.label,
            content = parameters.content,
        )

    /**
     * Composes a [Box] around an [AnimatedVisibility] with these values for its visibility, padding, durations,
     * transition name and content.
     */
    @Composable
    public fun Content(
        visible: Boolean,
        modifierPadding: Int,
        enterDurationMillis: Int,
        exitDurationMillis: Int,
        label: String,
        content: @Composable AnimatedVisibilityScope.() -> Unit,
    ) {
        Box {
            AnimatedVisibility(
                visible = visible,
                modifier = SwingModifier.padding(modifierPadding),
                enter = fadeIn(tween(enterDurationMillis)),
                exit = fadeOut(tween(exitDurationMillis)),
                label = label,
                content = content,
            )
        }
    }

    /** Builds the raw panel with a container panel holding a label, before any value is applied. */
    override fun createRawSwing(): JPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            add(newContainer(0), BorderLayout.CENTER)
        }

    /**
     * Applies the end state of [parameters] to [component]: the container is present while visible and gone while
     * hidden, as the composed side emits no node for it; the padding is an empty border on the container, so it goes
     * with it, and the label shows the text of the content. The durations and the transition name shape only the way
     * the end state is reached, so they set nothing.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: AnimatedVisibilityParameters,
    ) {
        val container = component.components.filterIsInstance<JPanel>().singleOrNull()
        if (parameters.visible) {
            val shown =
                container ?: newContainer(parameters.modifierPadding).also {
                    component.add(it, BorderLayout.CENTER)
                }
            val label = shown.components.single { it is JLabel } as JLabel
            label.text =
                when (parameters.content) {
                    BeforeContent -> BEFORE_TEXT
                    AfterContent -> AFTER_TEXT
                    else -> error("AnimatedVisibility renders only its own BeforeContent and AfterContent")
                }
            if (container == null) {
                component.revalidate()
            } else if (shown.insets.top != parameters.modifierPadding) {
                shown.border = emptyBorder(parameters.modifierPadding)
                component.revalidate()
            }
        } else if (container != null) {
            val bounds = container.bounds
            component.remove(container)
            component.revalidate()
            component.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    private fun newContainer(padding: Int): JPanel =
        JPanel().apply {
            isOpaque = false
            border = emptyBorder(padding)
            layout = OverlayLayout(this)
            add(JLabel())
        }

    private fun emptyBorder(size: Int) = EmptyBorder(size, size, size, size)
}

/** The label text shown while [AnimatedVisibilityParameters.content] is the before content. */
private const val BEFORE_TEXT = "Before: 80"

/** The label text shown while [AnimatedVisibilityParameters.content] is the after content. */
private const val AFTER_TEXT = "After: 60"

/** The content that shows the before text. */
private val BeforeContent: @Composable AnimatedVisibilityScope.() -> Unit = { Label(BEFORE_TEXT) }

/** The content that shows the after text. */
private val AfterContent: @Composable AnimatedVisibilityScope.() -> Unit = { Label(AFTER_TEXT) }
