// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.custom

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Point
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CustomComponentSectionTest {
    @Test
    fun theCustomComponentSectionWrapsAJComponentThroughSwingNode() =
        runComposeSwingTest {
            openSection("Custom component")

            onNodeWithText("3 / 5", substring = true).assertExists()
            val stars = onNodeWithTag(STAR_RATING_TAG)
            val box = stars.fetch().height
            stars.performClick(position = Point(4 * box + box / 2, box / 2))
            onNodeWithText("5 / 5", substring = true).assertExists()
        }

    @Test
    fun theCustomWidgetRendersToAStableBitmapScreenshotTest() =
        runComposeSwingTest {
            openSection("Custom component")

            val stars = onNodeWithTag(STAR_RATING_TAG).captureToImage()

            onNodeWithTag(STAR_RATING_TAG).assertImageMatches(expected = stars)

            assertFailsWith<AssertionError> {
                onNode(SwingMatcher.hasText("Clear") and SwingMatcher.isOfType<JButton>())
                    .assertImageMatches(expected = stars)
            }
        }
}
