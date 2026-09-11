package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.accessibility.mnemonic
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A default of each kind of property element: one put back as it stood, one the component derives again,
 * and one several unrelated component types declare - followed as its value changes, beside a declaration
 * it never overrides, and given back on removal. An element that is not inheritable is refused as a default,
 * whatever its kind.
 */
class ComponentDefaultsElementKindTest {
    @Test
    fun aRestoredDefaultFollowsItsValueAndGivesBackTheComponentsOwnOnRemoval() = runComposeSwingTest {
        val own = JLabel().background
        var provided by mutableStateOf<Color?>(Color.RED)
        setContent {
            ProvideComponentDefaults(DefaultBackground provides provided) {
                Label("inheriting")
                Label("declaring", modifier = SwingModifier.background(Color.BLUE))
            }
        }
        val (inheriting, declaring) = onAllNodesOfType<JLabel>().fetchAll()
        assertEquals(Color.RED, inheriting.background)
        assertEquals(Color.BLUE, declaring.background)

        provided = Color.GREEN
        awaitIdle()
        assertEquals(Color.GREEN, inheriting.background, "a changed default reaches the component inheriting it")
        assertEquals(Color.BLUE, declaring.background, "a changed default leaves a declaration standing")

        provided = null
        awaitIdle()
        assertSame(own, inheriting.background, "a removed default gives back the background the label held")
        assertTrue(inheriting.isBackgroundSet, "a removed default leaves the label its own background")
        assertEquals(Color.BLUE, declaring.background, "a removed default leaves a declaration standing")
    }

    @Test
    fun aDerivedDefaultFollowsItsValueAndHandsTheFlagBackToTheFillOnRemoval() = runComposeSwingTest {
        val filled = JButton().isContentAreaFilled
        var provided by mutableStateOf<Boolean?>(!filled)
        setContent {
            ProvideComponentDefaults(DefaultOpaque provides provided) {
                Button("inheriting", onClick = {})
                Button("declaring", onClick = {}, modifier = SwingModifier.opaque(!filled))
            }
        }
        val (inheriting, declaring) = onAllNodesOfType<JButton>().fetchAll()
        assertEquals(!filled, inheriting.isOpaque)

        provided = filled
        awaitIdle()
        assertEquals(filled, inheriting.isOpaque, "a changed default reaches the component inheriting it")
        assertEquals(!filled, declaring.isOpaque, "a changed default leaves a declaration standing")

        // The fill moves while the default stands, so the flag the removal hands back is the one the fill
        // derives now rather than the one the button held when the default arrived.
        inheriting.isContentAreaFilled = !filled
        awaitIdle()
        assertEquals(filled, inheriting.isOpaque, "the default holds the flag against the fill")

        provided = null
        awaitIdle()
        assertEquals(!filled, inheriting.isOpaque, "a removed default leaves the flag following the fill")
        assertEquals(!filled, declaring.isOpaque, "a removed default leaves a declaration standing")
    }

    @Test
    fun aMultiTargetDefaultFollowsItsValueOnEveryServedTypeAndGivesBackEachOwnOnRemoval() = runComposeSwingTest {
        val alignment = componentDefaultKeyOf<Int>("alignment") { horizontalAlignment(it) }
        var provided by mutableStateOf<Int?>(null)
        setContent {
            ProvideComponentDefaults(alignment provides provided) {
                Label("inheriting")
                Button("inheriting", onClick = {})
                Label("declaring", modifier = SwingModifier.horizontalAlignment(SwingConstants.LEFT))
            }
        }
        val (label, declaring) = onAllNodesOfType<JLabel>().fetchAll()
        val button = onNodeOfType<JButton>().fetch()
        // Each holds an alignment of its own that neither constructor gives.
        val labelOwn = SwingConstants.LEFT
        val buttonOwn = SwingConstants.RIGHT
        label.horizontalAlignment = labelOwn
        button.horizontalAlignment = buttonOwn

        provided = SwingConstants.CENTER
        awaitIdle()
        assertEquals(SwingConstants.CENTER, label.horizontalAlignment)
        assertEquals(SwingConstants.CENTER, button.horizontalAlignment)

        provided = SwingConstants.TRAILING
        awaitIdle()
        assertEquals(SwingConstants.TRAILING, label.horizontalAlignment, "a changed default reaches a label")
        assertEquals(SwingConstants.TRAILING, button.horizontalAlignment, "a changed default reaches a button")
        assertEquals(
            SwingConstants.LEFT,
            declaring.horizontalAlignment,
            "a changed default leaves a declaration standing",
        )

        provided = null
        awaitIdle()
        assertEquals(labelOwn, label.horizontalAlignment, "a removed default gives back the label's own alignment")
        assertEquals(buttonOwn, button.horizontalAlignment, "a removed default gives back the button's own alignment")
        assertEquals(
            SwingConstants.LEFT,
            declaring.horizontalAlignment,
            "a removed default leaves a declaration standing",
        )
    }

    @Test
    fun providerRejectsANonInheritableElementOfEveryKind() {
        val kinds: List<Pair<String, SwingModifier.(String) -> SwingModifier>> =
            listOf(
                "restored" to { toolTip(it) },
                "derived" to { accessibleName(it) },
                "declaredOnly" to { clientProperty("key", it) },
                "declaredThroughProperty" to {
                    property<JLabel, String?>(
                        name = "text",
                        value = it,
                        read = { label -> label.text },
                        write = { label, value -> label.text = value },
                    )
                },
                "multiTarget" to { mnemonic(KeyEvent.VK_A) },
            )
        for ((name, apply) in kinds) {
            val key = componentDefaultKeyOf(name, apply)
            val failure =
                assertFailsWith<IllegalArgumentException>("A $name element must be refused as a default") {
                    runComposeSwingTest {
                        setContent {
                            ProvideComponentDefaults(key provides "value") {
                                Label("error")
                            }
                        }
                    }
                }
            assertTrue(
                failure.message.orEmpty().startsWith(
                    "ComponentDefaultKey '$name' cannot declare non-inheritable element: ",
                ),
                "A $name element must be rejected as non-inheritable, but the provider said: ${failure.message}",
            )
        }
    }
}
