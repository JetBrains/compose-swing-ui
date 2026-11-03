// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import kotlin.test.Test

/** Behavioral tests for a [DrawModifierNode] whose component moves to another composition. */
class DrawModifierNodeMoveTest {
    @Test
    fun aDrawNodeMovedToAnotherCompositionAndRemovedThereFailsNothingWhenAReadItPaintedChanges() =
        runComposeSwingTest {
            var inOuter by mutableStateOf(false)
            var shownInOuter by mutableStateOf(true)
            var color by mutableStateOf(Color.RED)
            var hosted by mutableStateOf(true)
            lateinit var content: @Composable () -> Unit
            setContent {
                content =
                    remember {
                        movableContentOf {
                            DecoratedBox(
                                modifier =
                                    SwingModifier
                                        .testTag("box")
                                        .drawBehind { drawRect(color) },
                                style = { preferredSize(Dimension(16, 16)) },
                            )
                        }
                    }
                if (hosted) NestedContentHost(layout = FlowLayout()) { if (!inOuter) content() }
                if (inOuter && shownInOuter) content()
            }
            awaitIdle()
            onNodeWithTag("box").captureToImage()

            inOuter = true
            awaitIdle()
            shownInOuter = false
            awaitIdle()
            color = Color.BLUE
            awaitIdle()

            hosted = false
            awaitIdle()
        }
}
