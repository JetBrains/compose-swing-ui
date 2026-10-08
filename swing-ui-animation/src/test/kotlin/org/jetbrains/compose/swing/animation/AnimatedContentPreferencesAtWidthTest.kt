package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.foundation.layout.DefaultLeaves
import org.jetbrains.compose.swing.foundation.layout.PreferenceSlots
import org.jetbrains.compose.swing.foundation.layout.WRAPPING_TEXT_AREA_PANEL
import org.jetbrains.compose.swing.foundation.layout.assertPreferencesSettle
import kotlin.test.Test

class AnimatedContentPreferencesAtWidthTest {
    @Test
    fun `a stock parent's preference read leaves no work behind while a content enters`() =
        assertPreferencesSettle(
            contentContainers(leafEnters = true, insideBox = false, finished = false, widthFromLeafHeight = false),
            transitionTime = PartWay,
        )

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

    @Test
    fun `a stock parent's preference read leaves no work behind after a content entered`() =
        assertPreferencesSettle(
            contentContainers(leafEnters = true, insideBox = false, finished = true, widthFromLeafHeight = false),
            widthFromLeafHeight =
                contentContainers(leafEnters = true, insideBox = false, finished = true, widthFromLeafHeight = true),
        )

    @Test
    fun `a stock parent's preference read of a Box around it leaves no work behind after a content entered`() =
        assertPreferencesSettle(
            ContentEnteringInBoxContainers,
            widthFromLeafHeight = RatioContentEnteringInBoxContainers,
        )
}
