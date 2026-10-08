package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/** A container under a stock parent that reads its minimum size alone, and grants it a width of its own. */
class MinimumOnlyStockParentTest {
    @Test
    fun aContainerAParentUsesNaturalPreferredWidthForMinimumHeightWithoutPaintReconciliation() {
        for (granted in listOf(300, 60)) {
            runComposeSwingTest {
                setStockContent(
                    JPanel(
                        ScriptedStockLayout { child, _ ->
                            child.setBounds(0, 0, granted, child.minimumSize.height)
                        },
                    ),
                    width = 600,
                    height = 300,
                ) {
                    Layout(
                        ScriptedMeasurePolicy(
                            minimumWidth = { 50 },
                            preferredWidth = { 100 },
                            minimumHeight = { it / 2 },
                            preferredHeight = { it / 2 },
                            measureSize = { Dimension(it.maxWidth, it.maxWidth / 2) },
                        ),
                        SwingModifier.testTag("box"),
                    )
                }
                val box = onNodeWithTag("box").fetch<JComponent>()
                awaitIdle()

                assertEquals(
                    Dimension(granted, 100 / 2),
                    box.size,
                    "$granted wide: minimum height uses maximum intrinsic width",
                )

                val parent = box.parent as JComponent
                parent.revalidate()
                awaitIdle()

                assertEquals(
                    Dimension(granted, 100 / 2),
                    box.size,
                    "$granted wide: explicit validation preserves the natural preferred-width height",
                )
            }
        }
    }
}
