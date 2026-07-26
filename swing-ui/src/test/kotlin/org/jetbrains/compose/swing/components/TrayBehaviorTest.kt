package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioural tests for the icon a [Tray] registers with the live system tray: what it starts out
 * carrying, and that every declared property reaches it and keeps following the state that drives it.
 *
 * The icon is found among the icons the system tray holds by the image it was declared with, so the
 * assertions read the icon the composition actually registered rather than a stand-in. A live system
 * tray exists only where the platform provides one, so each case is skipped where it does not.
 */
class TrayBehaviorTest {
    @Test
    fun aTrayIconStartsWhereABareOneDoes() = runComposeSwingTest {
        assumeTrue(SystemTray.isSupported(), "requires a platform system tray")
        val image = trayImage()
        val bare = TrayIcon(image)

        setContent { Tray(image = image) }

        val icon = trayIconShowing(image)
        // A tray icon that declares neither property is left exactly as a hand-built one starts out:
        // no hover text, and the image painted at its own size.
        assertEquals(bare.toolTip, icon.toolTip, "tooltip")
        assertEquals(bare.isImageAutoSize, icon.isImageAutoSize, "imageAutoSize")
        assertNull(icon.toolTip, "an icon that declares no tooltip should show none")
        assertFalse(icon.isImageAutoSize, "an icon that declares no image sizing should paint its image as is")
    }

    @Test
    fun aDeclaredTooltipAndImageSizingReachTheTrayIcon() = runComposeSwingTest {
        assumeTrue(SystemTray.isSupported(), "requires a platform system tray")
        val image = trayImage()

        setContent { Tray(image = image, tooltip = "Reports", imageAutoSize = true) }

        val icon = trayIconShowing(image)
        assertEquals("Reports", icon.toolTip, "the declared tooltip should reach the icon")
        assertTrue(icon.isImageAutoSize, "the declared image sizing should reach the icon")
    }

    @Test
    fun theTrayIconFollowsTheStateDrivingIt() = runComposeSwingTest {
        assumeTrue(SystemTray.isSupported(), "requires a platform system tray")
        val first = trayImage()
        val second = trayImage()
        var image by mutableStateOf(first)
        var tooltip by mutableStateOf<String?>("Idle")
        var imageAutoSize by mutableStateOf(true)

        setContent { Tray(image = image, tooltip = tooltip, imageAutoSize = imageAutoSize) }

        val icon = trayIconShowing(first)
        assertEquals("Idle", icon.toolTip, "the icon should start at the declared tooltip")
        assertTrue(icon.isImageAutoSize, "the icon should start at the declared image sizing")

        image = second
        tooltip = "Working"
        imageAutoSize = false
        awaitIdle()

        assertEquals(second, icon.image, "the icon should follow the state driving its image")
        assertEquals("Working", icon.toolTip, "the icon should follow the state driving its tooltip")
        assertFalse(icon.isImageAutoSize, "the icon should follow the state driving its image sizing")

        tooltip = null
        awaitIdle()
        assertNull(icon.toolTip, "withdrawing the tooltip should take the hover text off the icon")
    }

    /** The single icon the system tray holds for [image]. */
    private fun ComposeSwingTest.trayIconShowing(image: Image): TrayIcon =
        SystemTray.getSystemTray().trayIcons.single { it.image === image }

    /** A distinct image, so the icon composed with it is the one found in the system tray. */
    private fun trayImage(): Image = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)

    private companion object {
        const val ICON_SIZE = 16
    }
}
