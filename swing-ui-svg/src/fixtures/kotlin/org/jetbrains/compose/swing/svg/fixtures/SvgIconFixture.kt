// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg.fixtures

import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.style.icon
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.svg.SvgIcon
import org.jetbrains.compose.swing.svg.rememberSvgIcon
import java.awt.Dimension
import javax.swing.JLabel

/** The icon size [SvgIconFixture] varies. */
internal data class SvgIconParameters(
    val size: Dimension,
)

/** A label with a resizable SVG icon paired with a plain Swing label drawing the same document. */
internal object SvgIconFixture : ComposableFixture<SvgIconParameters, JLabel>(
    initial = SvgIconParameters(size = Dimension(32, 32)),
) {
    private const val LABEL_TEXT = "SVG"

    override val alternatives: List<SvgIconParameters> = listOf(initial.copy(size = Dimension(64, 48)))

    @Composable
    override fun Content(parameters: SvgIconParameters): Unit = Content(parameters.size)

    /** Composes a label with the SVG icon at [size]. */
    @Composable
    private fun Content(size: Dimension) {
        val svgIcon = rememberSvgIcon(staticSvgDocument, size)
        Label(text = LABEL_TEXT, style = { icon(svgIcon) })
    }

    override fun createRawSwing(): JLabel = JLabel(LABEL_TEXT)

    @RestrictTo(RestrictTo.Scope.TESTS)
    override fun updateRawSwing(
        component: JLabel,
        parameters: SvgIconParameters,
    ) {
        component.icon = SvgIcon(staticSvgDocument, parameters.size.width, parameters.size.height, antialias = true)
    }
}
