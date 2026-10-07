/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.tooling.findDeclaringGroup
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class AnimationModifierUpstreamParityTest {
    private fun ComposeSwingTest.layoutRootRow() {
        val row = onNodeWithTag("root-row").fetch<JComponent>()
        row.size = row.preferredSize
        row.doLayout()
    }

    @Test
    fun animateContentSizeReattachingMovableContentKeepsTheTargetSize() =
        runComposeSwingTest {
            val expanded = mutableStateOf(false)
            var inFirstContainer by mutableStateOf(true)
            setContent {
                MovableAnimatedContent(expanded, inFirstContainer)
            }
            awaitIdle()
            mainClock.autoAdvance = false

            expanded.value = true
            driveOneFrame()
            driveOneFrame()
            awaitIdle()
            mainClock.advanceTimeBy(80.milliseconds, ignoreFrameDuration = true)
            awaitIdle()
            layoutRootRow()
            assertEquals(150, onNodeWithTag("first-width").fetch<JComponent>().x)
            assertEquals(150, onNodeWithTag("first-height").fetch<JComponent>().y)

            inFirstContainer = false
            awaitIdle()
            layoutRootRow()
            mainClock.autoAdvance = true
            awaitIdle()
            layoutRootRow()

            assertEquals(200, onNodeWithTag("second-width").fetch<JComponent>().x)
            assertEquals(200, onNodeWithTag("second-height").fetch<JComponent>().y)
        }

    @Test
    fun animateContentSizeIsExposedThroughTheEffectiveModifierChain() =
        runComposeSwingTest {
            val animationSpec = tween<Dimension>(160, easing = LinearEasing)
            val finishedListener: (Dimension, Dimension) -> Unit = { _, _ -> }
            val originalInspectorSetting = isDebugInspectorInfoEnabled
            isDebugInspectorInfoEnabled = true
            try {
                setContent {
                    Row {
                        Box(
                            modifier =
                                SwingModifier.testTag("inspected-content").animateContentSize(
                                    animationSpec = animationSpec,
                                    finishedListener = finishedListener,
                                ),
                        ) {
                            Box(modifier = SwingModifier.preferredSize(Dimension(100, 100)))
                        }
                    }
                }
                val component = onNodeWithTag("inspected-content").fetch<JComponent>()
                val node = assertNotNull(component.findDeclaringGroup()?.node as? SwingComponentNode<*>)
                val values: List<Pair<String, Map<String, Any?>>> =
                    node.modifier.foldIn(emptyList()) { entries, element ->
                        val inspectable = element as? SwingModifier.InspectableElement
                        if (inspectable == null) entries else entries + (inspectable.name to inspectable.declaredValues)
                    }

                assertEquals(
                    mapOf<String, Any?>(
                        "animationSpec" to animationSpec,
                        "alignment" to Alignment.TopStart,
                        "finishedListener" to finishedListener,
                    ),
                    values.single { it.first == "animateContentSize" }.second,
                )
            } finally {
                isDebugInspectorInfoEnabled = originalInspectorSetting
            }
        }

    @Composable
    private fun MovableAnimatedContent(
        expanded: State<Boolean>,
        inFirstContainer: Boolean,
    ) {
        val moving =
            remember {
                movableContentOf {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("moving-content")
                                .animateContentSize(tween(160, easing = LinearEasing)),
                    ) {
                        Box(
                            modifier =
                                SwingModifier.preferredSize(
                                    if (expanded.value) {
                                        Dimension(200, 200)
                                    } else {
                                        Dimension(100, 100)
                                    },
                                ),
                        )
                    }
                }
            }
        Row(modifier = SwingModifier.testTag("root-row")) {
            Box(modifier = SwingModifier.preferredSize(Dimension(300, 300))) {
                Column {
                    Row {
                        if (inFirstContainer) moving()
                        Box(modifier = SwingModifier.testTag("first-width").preferredSize(Dimension(1, 1)))
                    }
                    Box(modifier = SwingModifier.testTag("first-height").preferredSize(Dimension(1, 1)))
                }
            }
            Box(modifier = SwingModifier.preferredSize(Dimension(300, 300))) {
                Column {
                    Row {
                        if (!inFirstContainer) moving()
                        Box(modifier = SwingModifier.testTag("second-width").preferredSize(Dimension(1, 1)))
                    }
                    Box(modifier = SwingModifier.testTag("second-height").preferredSize(Dimension(1, 1)))
                }
            }
        }
    }
}
