package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JPanel

/** What a component reports when it has no baseline at all, as `java.awt.Component` defines it. */
internal const val NO_BASELINE = -1

/** A child of the fixture's own size whose component reports [baseline] wherever it is asked. */
@Composable
internal fun BaselineChild(
    baseline: Int,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { BaselinePanel(baseline) },
        modifier = modifier.styleable({ preferredSize(CHILD_WIDTH, CHILD_HEIGHT) }, RawKind),
    )
}

/** A child whose component reports [baseline] for every positive extent it can occupy. */
@Composable
internal fun FlexibleBaselineChild(
    baseline: Int,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { FlexibleBaselinePanel(baseline) },
        modifier = modifier.styleable({ preferredSize(CHILD_WIDTH, CHILD_HEIGHT) }, RawKind),
    )
}

/**
 * A component carrying the baseline the test chose for it, and none at all when it is asked at any size
 * other than the one it occupies - so a row asking the wrong question gets no baseline to place it by.
 */
internal class BaselinePanel(
    private val reported: Int,
) : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = if (width == CHILD_WIDTH && height == CHILD_HEIGHT) reported else NO_BASELINE
}

/** A component that retains its baseline when a layout modifier changes its extent. */
internal class FlexibleBaselinePanel(
    private val reported: Int,
) : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = if (width > 0 && height > 0) reported else NO_BASELINE
}
