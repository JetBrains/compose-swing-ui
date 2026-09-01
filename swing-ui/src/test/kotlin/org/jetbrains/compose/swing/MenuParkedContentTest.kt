package org.jetbrains.compose.swing

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Menu content the composition parks - an inactive [ReusableContentHost] - leaves the popup while the
 * runtime keeps the group's place in the composition, so the two index spaces diverge: the runtime goes
 * on counting the parked items, the popup no longer holds them. The menu must keep following its
 * declaration across that divergence - a sibling composed after the parked group lands where the
 * declaration puts it, and reactivation, which replaces the parked node with a fresh one, replaces the
 * right item.
 */
class MenuParkedContentTest {
    @Test
    fun aMenuFollowsItsContentAcrossParkingAndReactivation() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var sibling by mutableStateOf(false)
        val popup =
            composeMenu {
                ReusableContentHost(active = active) {
                    MenuItem("first", onClick = {})
                }
                MenuItem("last", onClick = {})
                if (sibling) MenuItem("extra", onClick = {})
            }
        assertEquals(listOf("first", "last"), popup.menuItemTexts())

        active = false
        awaitIdle()
        assertEquals(listOf("last"), popup.menuItemTexts(), "a parked item leaves the menu")

        sibling = true
        awaitIdle()
        assertEquals(
            listOf("last", "extra"),
            popup.menuItemTexts(),
            "an item composed after the parked group must land after the items still shown",
        )

        active = true
        awaitIdle()
        assertEquals(
            listOf("first", "last", "extra"),
            popup.menuItemTexts(),
            "reactivating must put a fresh item back at the parked group's place",
        )
    }
}
