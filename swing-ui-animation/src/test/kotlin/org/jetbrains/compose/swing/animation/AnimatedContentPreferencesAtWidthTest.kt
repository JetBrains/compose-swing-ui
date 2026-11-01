package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.layout.ContainerContent
import org.jetbrains.compose.swing.foundation.layout.DefaultLeaves
import org.jetbrains.compose.swing.foundation.layout.PreferenceSlots
import org.jetbrains.compose.swing.foundation.layout.WRAPPING_TEXT_AREA_PANEL
import org.jetbrains.compose.swing.foundation.layout.assertPreferencesSettle
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import java.util.stream.Stream
import javax.swing.JPanel
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
                        "JPanel preferring 200 by 10" to { modifier ->
                            SwingNode(
                                factory = { JPanel().apply { preferredSize = Dimension(200, 10) } },
                                modifier = modifier,
                            )
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
                preferenceCase("entering directly", PartWay, leafEnters = true, insideBox = false, finished = false),
                preferenceCase("entered directly", null, leafEnters = true, insideBox = false, finished = true),
                preferenceCase("entering inside Box", PartWay, leafEnters = true, insideBox = true, finished = false),
                preferenceCase("entered inside Box", null, leafEnters = true, insideBox = true, finished = true),
                preferenceCase("leaving inside Box", PartWay, leafEnters = false, insideBox = true, finished = false),
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
            leafEnters: Boolean,
            insideBox: Boolean,
            finished: Boolean,
        ): Arguments =
            Arguments.of(
                Named.of(name, contentContainers(leafEnters, insideBox, finished, widthFromLeafHeight = false)),
                contentContainers(leafEnters, insideBox, finished, widthFromLeafHeight = true),
                transitionTime,
            )
    }

    @Test
    fun `a stock parent's preference read leaves no work behind while a content leaves`() {
        val scrollPane = "JScrollPane 400 high"
        val containers =
            contentContainers(leafEnters = false, insideBox = false, finished = false, widthFromLeafHeight = false)
        val wrappingPanelContainers =
            listOf("fade", "scale", "expand and shrink").flatMap { kind ->
                listOf("clipped", "unclipped").map { sizing ->
                    "AnimatedContent under the parent, $kind, $sizing size transform, leaf filling the width"
                }
            }
        assertPreferencesSettle(
            containers.filterKeys { it !in wrappingPanelContainers && "slide" !in it },
            transitionTime = PartWay,
        )
        assertPreferencesSettle(
            containers.filterKeys { "slide" in it },
            transitionTime = SlideExitTime,
        )
        // A wrapping JTextArea in a JPanel does not settle under the scroll pane slot.
        assertPreferencesSettle(
            containers.filterKeys { it in wrappingPanelContainers },
            slots =
                PreferenceSlots.mapValues { DefaultLeaves } +
                    (scrollPane to DefaultLeaves - WRAPPING_TEXT_AREA_PANEL),
            transitionTime = PartWay,
        )
    }
}
