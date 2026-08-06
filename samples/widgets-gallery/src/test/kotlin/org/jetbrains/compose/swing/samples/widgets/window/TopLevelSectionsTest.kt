package org.jetbrains.compose.swing.samples.widgets.window

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class TopLevelSectionsTest {
    @Test
    fun theWindowsSectionMountsWithBothPeersClosed() =
        runComposeSwingTest {
            openSection("Top-level windows")

            onNodeWithText("Window is closed", substring = true).assertExists()
            onNodeWithText("No dialog acknowledged yet", substring = true).assertExists()
        }

    // The window card reads its hoisted WindowState into labels and writes it from buttons, so the
    // readout tracks a state change made from the composition's own side of the two-way contract.
    @Test
    fun theWindowGeometryReadoutFollowsTheStateAControlDrives() =
        runComposeSwingTest {
            openSection("Top-level windows")

            onNodeWithText("Position: PlatformDefault", substring = true).assertExists()
            onNodeWithText("Size: 320 x 200", substring = true).assertExists()

            onNodeWithText("Widen by 40").performClick()
            onNodeWithText("Size: 360 x 200", substring = true).assertExists()

            onNodeWithText("Centre on screen").performClick()
            onNodeWithText("Position: CenteredOnScreen", substring = true).assertExists()
        }

    @Test
    fun theDesktopPaneAddsAndControlledClosesAnInternalFrame() =
        runComposeSwingTest {
            openSection("Layered & MDI")

            onNodeWithText("Controlled closes: 0", substring = true).assertExists()
            onNodeWithText("Inspector frame", substring = true).assertDoesNotExist()

            onNodeWithText("Add frame").performClick()
            onNodeWithText("Inspector frame", substring = true).assertExists()

            onNodeWithText("Remove frame").performClick()
            onNodeWithText("Inspector frame", substring = true).assertDoesNotExist()
        }

    @Test
    fun theLayeredPaneStacksItsDepthLayers() =
        runComposeSwingTest {
            openSection("Layered & MDI")

            onNodeWithText("Default layer", substring = true).assertExists()
            onNodeWithText("Palette layer", substring = true).assertExists()
            onNodeWithText("Drag layer (top)", substring = true).assertExists()
        }

    @Test
    fun theTraySectionMountsWithTheIconHidden() =
        runComposeSwingTest {
            openSection("System tray")

            onNodeWithText("Tray icon: hidden", substring = true).assertExists()
            onNodeWithText("Last action: none", substring = true).assertExists()
            onNodeWithText("Notifications: on", substring = true).assertExists()
        }
}
