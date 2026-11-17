// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.foundation.layout.ContainerContent
import org.jetbrains.compose.swing.foundation.layout.DefaultLeaves
import org.jetbrains.compose.swing.foundation.layout.PreferenceSlots
import org.jetbrains.compose.swing.foundation.layout.WRAPPING_TEXT_AREA_PANEL
import org.jetbrains.compose.swing.foundation.layout.assertPreferencesSettle
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.preferredSize
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.time.Duration

class AnimatedContentPreferencesAtWidthTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("preferenceCases")
    fun aStockParentsPreferenceReadLeavesNoWorkBehind(
        containers: Map<String, ContainerContent>,
        widthFromLeafHeight: Map<String, ContainerContent>,
        transitionTime: Duration?,
    ) = assertPreferencesSettle(
        containers,
        transitionTime = transitionTime,
        widthFromLeafHeight = widthFromLeafHeight,
    )

    // The "Other content" label is gone and the container holds only the leaf. The leaf is wider than that label and
    // shorter than it, so a label still counted, or an animated size that stops short of the leaf, reports another
    // width or height. The leaves that fill the width at an aspect ratio take their height from the width the slot
    // grants, so they are left to the test above.
    @ParameterizedTest(name = "{0}")
    @MethodSource("enteredCases")
    fun anEnteredContentLeavesItsContainerPreferringTheLeafsSize(containers: Map<String, ContainerContent>) {
        assertPreferencesSettle(
            containers,
            slots =
                PreferenceSlots.mapValues {
                    mapOf<String, @Composable (SwingModifier) -> Unit>(
                        "Panel preferring 200 by 10" to { modifier ->
                            Panel(modifier = modifier, style = { preferredSize(200, 10) }) {}
                        },
                    )
                },
            sizeProblems = { container, _ ->
                if (container.preferredSize == Dimension(200, 10)) {
                    emptyList()
                } else {
                    listOf("the container prefers ${container.preferredSize}, not the leaf's 200 by 10")
                }
            },
        )
    }

    companion object {
        @JvmStatic
        fun preferenceCases(): Stream<Arguments> =
            Stream.of(
                preferenceCase(
                    "entering directly",
                    PartWay,
                    CaseShape(leafEnters = true, insideBox = false, finished = false),
                ),
                preferenceCase(
                    "entered directly",
                    null,
                    CaseShape(leafEnters = true, insideBox = false, finished = true),
                ),
                preferenceCase(
                    "entering inside Box",
                    PartWay,
                    CaseShape(leafEnters = true, insideBox = true, finished = false),
                ),
                preferenceCase(
                    "entered inside Box",
                    null,
                    CaseShape(leafEnters = true, insideBox = true, finished = true),
                ),
                preferenceCase(
                    "leaving inside Box",
                    PartWay,
                    CaseShape(leafEnters = false, insideBox = true, finished = false),
                    slide = false,
                ),
                preferenceCase(
                    "leaving inside Box, slide",
                    SlideExitTime,
                    CaseShape(leafEnters = false, insideBox = true, finished = false),
                    slide = true,
                ),
            )

        @JvmStatic
        fun enteredCases(): Stream<Arguments> =
            Stream.of(
                enteredCase("entered directly", insideBox = false),
                enteredCase("entered inside Box", insideBox = true),
            )

        private fun enteredCase(
            name: String,
            insideBox: Boolean,
        ): Arguments =
            Arguments.of(
                Named.of(
                    name,
                    contentContainerGroups(leafEnters = true, insideBox, finished = true, widthFromLeafHeight = false)
                        .atWidth,
                ),
            )

        private fun preferenceCase(
            name: String,
            transitionTime: Duration?,
            shape: CaseShape,
            slide: Boolean? = null,
        ): Arguments {
            fun containers(widthFromLeafHeight: Boolean) =
                contentContainers(shape.leafEnters, shape.insideBox, shape.finished, widthFromLeafHeight, slide)
            return Arguments.of(
                Named.of(name, containers(widthFromLeafHeight = false)),
                containers(widthFromLeafHeight = true),
                transitionTime,
            )
        }
    }

    @Test
    fun `a stock parent's preference read leaves no work behind while a content leaves`() {
        val scrollPane = "JScrollPane 400 high"
        val containers =
            contentContainers(
                leafEnters = false,
                insideBox = false,
                finished = false,
                widthFromLeafHeight = false,
                slide = false,
            )
        val wrappingPanelContainers =
            listOf("fade", "scale", "expand and shrink").flatMap { kind ->
                listOf("clipped", "unclipped").map { sizing ->
                    "AnimatedContent under the parent, $kind, $sizing size transform, leaf filling the width"
                }
            }
        assertPreferencesSettle(
            containers.filterKeys { it !in wrappingPanelContainers },
            transitionTime = PartWay,
        )
        assertPreferencesSettle(
            contentContainers(
                leafEnters = false,
                insideBox = false,
                finished = false,
                widthFromLeafHeight = false,
                slide = true,
            ),
            transitionTime = SlideExitTime,
        )
        // A wrapping JTextArea in a JPanel does not settle under the scroll pane slot.
        val wrappingPanelSlots =
            PreferenceSlots.mapValues { DefaultLeaves } + (scrollPane to DefaultLeaves - WRAPPING_TEXT_AREA_PANEL)
        assertPreferencesSettle(
            containers.filterKeys { it in wrappingPanelContainers },
            slots = wrappingPanelSlots,
            transitionTime = PartWay,
        )
    }
}

/** The content and container state one preference case is built from. */
private data class CaseShape(
    val leafEnters: Boolean,
    val insideBox: Boolean,
    val finished: Boolean,
)
