// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

internal class StyleableModifierTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun anEmptyStyleProducesEqualModifiersWithEqualHashCodes(predeclared: Boolean) {
        val first =
            (if (predeclared) SwingModifier.testTag("declared") else SwingModifier)
                .styleable(PlainProbe, SwingStyle)
        val second =
            (if (predeclared) SwingModifier.testTag("declared") else SwingModifier)
                .styleable(PlainProbe, SwingStyle)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun anEmptyStylePreservesTheDeclaredModifierAndComponentProperties() = runComposeSwingTest {
        var text by mutableStateOf("declared")
        setContent {
            SwingNode(
                factory = { JLabel("original") },
                modifier =
                    SwingModifier
                        .testTag("declared")
                        .property(LabelTextDescriptor, text)
                        .styleable(PlainProbe, SwingStyle),
            )
        }
        onNodeWithTag("declared").assertIsOfType<JLabel>().assertProperty("declared") { this.text }

        text = "updated"
        awaitIdle()

        onNodeWithTag("declared").assertIsOfType<JLabel>().assertProperty("updated") { this.text }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun capturesReuseStylesByStability(stable: Boolean) = runComposeSwingTest {
        val red = Color(255, 0, 0)
        var color by mutableStateOf(red, neverEqualPolicy())
        var hex by mutableStateOf("#FF0000", neverEqualPolicy())
        var pass by mutableIntStateOf(0)
        val probe = ProbeRecord()
        setContent {
            if (stable) ProbeHex(hex, pass, probe) else Probe(color, pass, probe)
        }
        onNodeOfType<JLabel>().assertProperty(red) { background }
        assertEquals(1, probe.resolves)
        assertEquals(1, probe.writes)
        val attached = probe.resolves

        repeat(if (stable) 1 else 2) {
            pass++
            awaitIdle()
        }
        if (stable) {
            hex = String("#FF0000".toCharArray())
            pass++
            awaitIdle()
        }
        assertEquals(attached, probe.resolves)
        assertEquals(1, probe.writes)

        if (!stable) {
            color = Color(255, 0, 0)
            awaitIdle()
            assertEquals(attached + 1, probe.resolves)
            assertEquals(1, probe.writes)
        }
        if (stable) hex = "#0000FF" else color = Color.BLUE
        awaitIdle()
        assertEquals(attached + if (stable) 1 else 2, probe.resolves)
        assertEquals(2, probe.writes)
        onNodeOfType<JLabel>().assertProperty(Color.BLUE) { background }
    }
}

private class ProbeRecord {
    var resolves = 0
    var writes = 0

    val kind =
        SwingStyleKind<CostProbeScope, Unit>(
            { base, _ ->
                object : CostProbeScope, ComponentStyleScope by base {
                    override val backgroundProperty = backgroundStyle
                }
            },
            { resolves++ },
        )

    private val backgroundStyle =
        stylePropertyOf(
            ComponentPropertyDescriptor<JComponent, Color?>(
                "background",
                { it.background },
                { component, value ->
                    writes++
                    component.background = value
                },
            ),
        )
}

private interface CostProbeScope : ComponentStyleScope {
    val backgroundProperty: SwingStyleProperty<Color?>
}

private fun CostProbeScope.background(color: Color) = backgroundProperty.provide(color)

/** A style lambda capturing only [c], written at the call site. */
@Composable
private fun Probe(
    c: Color,
    pass: Int,
    record: ProbeRecord,
) {
    readPass(pass)
    Styled(style = { background(c) }, record)
}

/** A style lambda capturing only [hex], written at the call site. */
@Composable
private fun ProbeHex(
    hex: String,
    pass: Int,
    record: ProbeRecord,
) {
    readPass(pass)
    Styled(style = { background(Color.decode(hex)) }, record)
}

@Composable
@NonSkippableComposable
private fun Styled(
    style: SwingStyle<CostProbeScope>,
    record: ProbeRecord,
) {
    SwingNode(factory = { JLabel() }, modifier = SwingModifier.styleable(record.kind, style))
}
