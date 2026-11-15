// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.OverlayLayout
import kotlin.math.roundToInt

/** The width the host offers its child, which both sides of [AspectRatioFixture] must agree on. */
private const val OFFER_WIDTH = 180

/** The height the host offers its child, which both sides of [AspectRatioFixture] must agree on. */
private const val OFFER_HEIGHT = 120

/** The parameters [AspectRatioFixture] varies: the arguments [aspectRatio] takes. */
public data class AspectRatioParameters(
    val ratio: Float,
    val matchHeightConstraintsFirst: Boolean,
)

/** A [Box] sized by [aspectRatio] inside a fixed-size host against a raw [JPanel] sized by hand. */
public object AspectRatioFixture : ComposableFixture<AspectRatioParameters, JPanel>(
    initial = AspectRatioParameters(ratio = 1f, matchHeightConstraintsFirst = false),
) {
    override val alternatives: List<AspectRatioParameters> =
        listOf(
            initial.copy(ratio = 2f),
            initial.copy(ratio = 0.5f),
            initial.copy(matchHeightConstraintsFirst = true),
        )

    @Composable
    override fun Content(parameters: AspectRatioParameters): Unit =
        Content(
            ratio = parameters.ratio,
            matchHeightConstraintsFirst = parameters.matchHeightConstraintsFirst,
        )

    /** Composes a [Box] sized by [aspectRatio] inside a host [Box] of a fixed size that offers it room to choose. */
    @Composable
    public fun Content(
        ratio: Float,
        matchHeightConstraintsFirst: Boolean,
    ) {
        Box {
            Box(modifier = SwingModifier.size(OFFER_WIDTH, OFFER_HEIGHT)) {
                Box(
                    modifier =
                        SwingModifier.aspectRatio(
                            ratio = ratio,
                            matchHeightConstraintsFirst = matchHeightConstraintsFirst,
                        ),
                )
            }
        }
    }

    /** Builds the raw wrapper holding the fixed-size host panel, which holds the child [updateRawSwing] sizes. */
    override fun createRawSwing(): JPanel =
        overlayPanel().apply {
            add(
                overlayPanel().apply {
                    preferredSize = Dimension(OFFER_WIDTH, OFFER_HEIGHT)
                    maximumSize = Dimension(OFFER_WIDTH, OFFER_HEIGHT)
                    add(overlayPanel())
                },
            )
        }

    /** Sizes the child of the host panel in [component] by the ratio of [parameters] and the host's width. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: AspectRatioParameters,
    ) {
        val size = Dimension(OFFER_WIDTH, (OFFER_WIDTH / parameters.ratio).roundToInt())
        val child = (component.components.single() as JPanel).components.single()
        child.preferredSize = size
        child.maximumSize = size
        component.revalidate()
    }

    private fun overlayPanel(): JPanel =
        JPanel().apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            layout = OverlayLayout(this)
        }
}
