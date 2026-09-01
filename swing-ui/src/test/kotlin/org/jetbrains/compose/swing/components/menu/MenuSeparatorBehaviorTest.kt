package org.jetbrains.compose.swing.components.menu

import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Behavioral coverage for [MenuSeparator]: the divider a menu draws between its items.
 *
 * A menu draws its divider with a widget of its own, `JPopupMenu.Separator`, whose UI class ID is what
 * a Look and Feel keys the divider's insets, height and color off. A declared separator is therefore
 * asserted against the one a hand-built menu holds, not merely against being a separator of some kind.
 */
class MenuSeparatorBehaviorTest {
    @Test
    fun aDeclaredSeparatorIsTheWidgetAMenuDividesItsItemsWith() = runComposeSwingTest {
        val popup =
            composeMenu {
                MenuItem("Cut", onClick = { })
                MenuSeparator()
                MenuItem("Paste", onClick = { })
            }

        assertEquals(
            listOf("Cut", null, "Paste"),
            popup.menuItemTexts(),
            "the separator should sit in the surrounding menu, in declaration order, among its items",
        )
        assertIs<JPopupMenu.Separator>(
            popup.getComponent(1),
            "a declared separator should be the separator widget a menu holds",
        )
    }

    @Test
    fun aDeclaredSeparatorIsDrawnAsAHandBuiltMenusIs() = runComposeSwingTest {
        val handBuilt = JPopupMenu().apply { addSeparator() }
        val popup = composeMenu { MenuSeparator() }

        assertEquals(
            (handBuilt.getComponent(0) as JComponent).uiClassID,
            (popup.getComponent(0) as JComponent).uiClassID,
            "a declared separator should ask the Look and Feel for the same drawing a hand-built one asks for",
        )
    }
}
