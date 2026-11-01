package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertEquals

/** A container under a stock parent that reads its minimum size alone, and grants it a width of its own. */
class MinimumOnlyStockParentTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("grantedWidths")
    fun aContainerAParentUsesNaturalPreferredWidthForMinimumHeightWithoutPaintReconciliation(granted: Int) {
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
                Dimension(granted, 50),
                box.size,
                "$granted wide: minimum height uses maximum intrinsic width",
            )

            val parent = box.parent as JComponent
            parent.revalidate()
            awaitIdle()

            assertEquals(
                Dimension(granted, 50),
                box.size,
                "$granted wide: explicit validation preserves the natural preferred-width height",
            )
        }
    }

    companion object {
        @JvmStatic
        fun grantedWidths(): List<Arguments> = listOf(300, 60).map { Arguments.of(Named.of("granted $it", it)) }
    }
}
