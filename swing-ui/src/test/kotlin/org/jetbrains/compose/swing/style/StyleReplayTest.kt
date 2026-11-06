package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** A providing ancestor's style reads the composition locals of the ancestor, however a descendant overrides them. */
class StyleReplayTest {
    @Test
    fun providerConditionUsesAncestorLocal() = runComposeSwingTest {
        setContent {
            Panel(style = {
                if (LocalFlag.currentValue) provideNestedStyle(Labels) { toolTip("provided") }
            }) {
                CompositionLocalProvider(LocalFlag provides false) { Label("label") }
            }
        }

        onNodeOfType<JLabel>().assertProperty("provided") { toolTipText }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("localKinds")
    fun providerValueUsesAncestorLocal(
        localTip: ProvidableCompositionLocal<String>,
        readInsideBlock: Boolean,
    ) = runComposeSwingTest {
        var tip by mutableStateOf("first")
        setContent {
            CompositionLocalProvider(localTip provides tip) {
                Panel(style = {
                    if (readInsideBlock) {
                        provideNestedStyle(Labels) { toolTip(localTip.currentValue) }
                    } else {
                        val read = localTip.currentValue
                        provideNestedStyle(Labels) { toolTip(read) }
                    }
                }) {
                    CompositionLocalProvider(localTip provides "override") { Label("label") }
                }
            }
        }
        onNodeOfType<JLabel>().assertProperty("first") { toolTipText }

        tip = "second"
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty("second") { toolTipText }
    }

    @Test
    fun additiveStyleIsRejected() {
        val additive = stylePropertyOf<Unit> { AdditiveElement }
        assertFailsWith<IllegalArgumentException> {
            runComposeSwingTest {
                setContent {
                    SwingNode(
                        factory = { JLabel() },
                        modifier = SwingModifier.styleable({ additive.provide(Unit) }, PlainProbe),
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun localKinds(): List<Arguments> = listOf(
            "static" to staticCompositionLocalOf { "unset" },
            "dynamic" to compositionLocalOf { "unset" },
        ).flatMap { (kind, local) ->
            listOf(false, true).map { inside ->
                val position = if (inside) "inside" else "outside"
                Arguments.of(Named.of("$kind local read $position the block", local), inside)
            }
        }
    }
}

private object AdditiveElement : SwingModifier.NodeElement<Component, SwingModifier.ComponentNode<Component>>() {
    override val targetType: Class<Component> get() = Component::class.java

    override val additive: Boolean get() = true

    override fun create(): SwingModifier.ComponentNode<Component> = object : SwingModifier.ComponentNode<Component>() {}

    override fun update(node: SwingModifier.ComponentNode<Component>) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 1
}

private val LocalFlag = staticCompositionLocalOf { true }
