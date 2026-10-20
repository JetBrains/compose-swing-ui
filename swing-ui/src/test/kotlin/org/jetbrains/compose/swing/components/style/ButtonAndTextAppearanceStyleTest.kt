package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestButtonKind
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Insets
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JMenuItem
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.nimbus.NimbusLookAndFeel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButtonAndTextAppearanceStyleTest {
    @Test
    fun opaqueDeclarationSurvivesFillChange() = runComposeSwingTest {
        underMetal {
            var filled by mutableStateOf(true)
            setContent {
                Button(
                    "Save",
                    onClick = { },
                    style = {
                        opaque(true)
                        contentAreaFilled(filled)
                    },
                )
            }

            val button = onNodeOfType<JButton>()
            button.assertProperty(true, "the declared flag is what the button carries") { isOpaque }

            filled = false
            awaitIdle()
            button.assertProperty(true, "the declared flag stands after the LaF reports the new fill") { isOpaque }
        }
    }

    @Test
    fun removedOpacityFollowsStandingFill() = runComposeSwingTest {
        underMetal {
            var declared by mutableStateOf(true)
            setContent {
                Button(
                    "Save",
                    onClick = { },
                    style = {
                        if (declared) opaque(true)
                        contentAreaFilled(false)
                    },
                )
            }

            val button = onNodeOfType<JButton>()
            button.assertProperty(true, "the declared flag is what the button carries") { isOpaque }

            declared = false
            awaitIdle()
            button.assertProperty(false, "dropping the flag leaves the button at the fill it declares") { isOpaque }
        }
    }

    @Test
    fun removedFillRestoresOriginalOpacity() = runComposeSwingTest {
        underMetal {
            var declared by mutableStateOf(true)
            setContent {
                SwingNode(
                    factory = { JButton("Save").apply { isOpaque = false } },
                    modifier = SwingModifier.styleable({ if (declared) contentAreaFilled(false) }, TestButtonKind),
                )
            }

            val button = onNodeOfType<JButton>()
            button.assertProperty(false, "the declared fill is what the button carries") { isContentAreaFilled }
            button.assertProperty(false, "the button is left at the flag it carried") { isOpaque }

            declared = false
            awaitIdle()

            button.assertProperty(true, "dropping the fill restores the one the button had") { isContentAreaFilled }
            button.assertProperty(false, "the flag nothing declares is left where the button carried it") { isOpaque }
        }
    }

    @Test
    fun mnemonicAndMarginRestoreIndependently() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        val declaredMargin = Insets(9, 9, 9, 9)
        setContent {
            Button(
                "Save",
                onClick = { },
                style = {
                    if (declared) {
                        mnemonic('S')
                        margin(declaredMargin)
                    }
                },
            )
        }

        val button = onNodeOfType<JButton>()
        var carried: Insets? = null
        button.assertProperty(true) {
            carried = margin
            true
        }

        declared = true
        awaitIdle()
        button.assertProperty(KeyEvent.VK_S, "the declared mnemonic is what the button carries") { mnemonic }
        button.assertProperty(declaredMargin, "the declared margin is what the button carries") { margin }

        declared = false
        awaitIdle()

        button.assertProperty(0, "dropping the mnemonic leaves the button without one") { mnemonic }
        button.assertProperty(carried, "and dropping the margin puts back the one it came with") { margin }
    }

    @Test
    fun staggeredFillAndOpacityRestoreOriginals() = runComposeSwingTest {
        underMetal {
            var flag by mutableStateOf(false)
            var fill by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { JButton("Save").apply { isOpaque = false } },
                    modifier =
                        SwingModifier.styleable(
                            {
                                if (fill) contentAreaFilled(false)
                                if (flag) opaque(true)
                            },
                            TestButtonKind,
                        ),
                )
            }

            val button = onNodeOfType<JButton>()
            button.assertProperty(true, "the button starts filled") { isContentAreaFilled }
            button.assertProperty(false, "and transparent, which is not the flag the fill implies") { isOpaque }
            flag = true
            awaitIdle()
            fill = true
            awaitIdle()
            flag = false
            awaitIdle()
            fill = false
            awaitIdle()

            button.assertProperty(true, "dropping the fill restores the one the button had") { isContentAreaFilled }
            button.assertProperty(false, "and the flag it came with rather than the one that fill implies") { isOpaque }
        }
    }

    @Test
    fun fillAndOpacityRemovedTogetherRestoreOriginal() = runComposeSwingTest {
        underMetal {
            var declared by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { JButton("Save") },
                    modifier =
                        SwingModifier.styleable(
                            {
                                if (declared) {
                                    contentAreaFilled(false)
                                    opaque(true)
                                }
                            },
                            TestButtonKind,
                        ),
                )
            }

            val button = onNodeOfType<JButton>()
            var original = false
            button.assertProperty(true) {
                original = isOpaque
                true
            }

            declared = true
            awaitIdle()
            declared = false
            awaitIdle()

            button.assertProperty(original, "removing both restores the flag the button was built with") { isOpaque }
        }
    }

    @Test
    fun removedMenuOpacityRestoresOriginal() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        val popup =
            composeMenu {
                MenuItem(
                    "Open",
                    onClick = { },
                    style = {
                        if (declared) opaque(false)
                        contentAreaFilled(false)
                    },
                )
            }
        val item = popup.getComponent(0) as JMenuItem
        assertFalse(item.isOpaque, "the declared flag is what the menu item carries")

        declared = false
        awaitIdle()
        assertEquals(
            JMenuItem("Open").isOpaque,
            item.isOpaque,
            "dropping the flag leaves the menu item where one carrying no declaration stands",
        )
    }

    @Test
    fun unchangedBorderPaintingFollowsLookAndFeel() = runComposeSwingTest {
        underMetal {
            val popup = composeMenu { MenuItem("Open", onClick = { }, style = { borderPainted(true) }) }
            val item = popup.getComponent(0) as JMenuItem
            assertTrue(item.isBorderPainted, "the declared painting is what the item shows")
            withLookAndFeelDefaults("MenuItem.borderPainted" to false) {
                SwingUtilities.updateComponentTreeUI(popup)
            }

            assertFalse(item.isBorderPainted, "the item follows the look and feel's answer")
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun lookAndFeelChangesOnlyUnwrittenValues(fill: Boolean) = runComposeSwingTest {
        underMetal {
            setContent {
                Button("Save", onClick = {}, style = {
                    if (fill) contentAreaFilled(true) else iconTextGap(4)
                })
                CheckBox("Wrap", checked = false, onCheckedChange = {}, style = {
                    if (fill) contentAreaFilled(false) else iconTextGap(11)
                })
            }
            val button = onNodeOfType<JButton>()
            val box = onNodeOfType<JCheckBox>()
            button.assertProperty(if (fill) true else 4) { if (fill) isContentAreaFilled else iconTextGap }
            box.assertProperty(if (fill) false else 11) { if (fill) isContentAreaFilled else iconTextGap }
            val defaults =
                if (fill) {
                    arrayOf("Button.contentAreaFilled" to false)
                } else {
                    arrayOf("Button.iconTextGap" to 13, "CheckBox.iconTextGap" to 13)
                }
            withLookAndFeelDefaults(*defaults) {
                UIManager.setLookAndFeel(NimbusLookAndFeel())
                SwingUtilities.updateComponentTreeUI(root)
            }
            button.assertProperty(if (fill) false else 13) { if (fill) isContentAreaFilled else iconTextGap }
            box.assertProperty(if (fill) false else 11) { if (fill) isContentAreaFilled else iconTextGap }
        }
    }

    @Test
    fun textFieldColorsUpdateAndRestore() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        var color by mutableStateOf(Color.RED)
        setContent {
            TextField("", onValueChange = {}, style = {
                if (declared) {
                    caretColor(color)
                    selectionColor(Color.GREEN)
                    selectedTextColor(Color.BLUE)
                    disabledTextColor(Color.GRAY)
                }
            })
        }
        val field = onNodeOfType<JTextField>()
        var installed: Color? = null
        field.assertProperty(true) {
            installed = caretColor
            true
        }
        declared = true
        awaitIdle()
        field.assertProperty(Color.RED) { caretColor }
        field.assertProperty(Color.GREEN) { selectionColor }
        field.assertProperty(Color.BLUE) { selectedTextColor }
        field.assertProperty(Color.GRAY) { disabledTextColor }
        color = Color.BLUE
        awaitIdle()
        field.assertProperty(Color.BLUE) { caretColor }
        declared = false
        awaitIdle()
        field.assertProperty(installed) { caretColor }
    }

    // UIManager overrides survive a look-and-feel change; remove them after observing the installed values.
    private inline fun <R> withLookAndFeelDefaults(
        vararg defaults: Pair<String, Any>,
        body: () -> R,
    ): R {
        defaults.forEach { (key, value) -> UIManager.put(key, value) }
        try {
            return body()
        } finally {
            defaults.forEach { (key, _) -> UIManager.put(key, null) }
        }
    }
}
