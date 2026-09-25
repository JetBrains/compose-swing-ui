package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a provided default covers: the content of its provider, and nothing beside it. */
class ComponentDefaultsScopeTest {
    @Test
    fun aDefaultProvidedToOneSubtreeStaysOutOfASiblingSubtree() = runComposeSwingTest {
        var color by mutableStateOf(Color.RED)
        setContent {
            ProvideComponentDefaults(DefaultBackground provides color) { Label("first") }
            ProvideComponentDefaults(DefaultForeground provides Color.BLUE) { Label("second") }
            Label("outside")
        }
        val own = JLabel()
        val first = onNodeWithText("first").fetch()
        val second = onNodeWithText("second").fetch()
        val outside = onNodeWithText("outside").fetch()

        for (expected in listOf(Color.RED, Color.GREEN)) {
            color = expected
            awaitIdle()

            assertEquals(expected, first.background, "$expected: the subtree the default is provided to")
            assertEquals(own.foreground, first.foreground, "$expected: the sibling's default stays out of it")
            assertEquals(own.background, second.background, "$expected: the default stays out of a sibling subtree")
            assertEquals(Color.BLUE, second.foreground, "$expected: the sibling keeps its own default")
            assertEquals(own.background, outside.background, "$expected: a component under no provider")
            assertEquals(own.foreground, outside.foreground, "$expected: a component under no provider")
        }
    }

    @Test
    fun aNestedProviderOverridingOneKeyKeepsTheOuterKeys() = runComposeSwingTest {
        var background by mutableStateOf(Color.RED)
        var foreground by mutableStateOf(Color.GREEN)
        setContent {
            ProvideComponentDefaults(DefaultBackground provides background, DefaultForeground provides foreground) {
                Label("outer")
                ProvideComponentDefaults(DefaultBackground provides Color.BLUE) { Label("inner") }
            }
        }
        val outer = onNodeWithText("outer").fetch()
        val inner = onNodeWithText("inner").fetch()
        assertEquals(Color.BLUE, inner.background, "the nested provision replaces the outer one for its key")
        assertEquals(Color.GREEN, inner.foreground, "the outer key it does not name still applies")

        foreground = Color.YELLOW
        awaitIdle()
        assertEquals(Color.YELLOW, inner.foreground, "a change of the outer key reaches under the nested provider")
        assertEquals(Color.BLUE, inner.background, "and leaves the nested provision standing")

        background = Color.CYAN
        awaitIdle()
        assertEquals(Color.CYAN, outer.background, "a change of the overridden key reaches the outer content")
        assertEquals(Color.BLUE, inner.background, "and stays replaced under the nested provider")
        assertEquals(Color.YELLOW, inner.foreground)
    }
}
