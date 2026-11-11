// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Font
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals

private val LocalProbe = compositionLocalOf { "default" }

class StyleLocalTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun localsSharingADescriptorUseTheLastDeclarationForParentAndDescendants(reverse: Boolean) = runComposeSwingTest {
        val alias = styleLocalOf(FirstDescriptor)
        val locals = if (reverse) listOf(alias, InheritedFirst) else listOf(InheritedFirst, alias)
        setContent {
            ProbeContainer(
                StyleProbe("parent"),
                {
                    locals[0].provide("first")
                    locals[1].provide("last")
                },
            ) {
                ProbeLeaf(StyleProbe("leaf"), SwingStyle, PlainProbe)
            }
        }
        onProbe("parent").assertProperty("last") { first }
        onProbe("leaf").assertProperty("last") { first }
    }

    @Test
    fun multipleParentStylesComposeLocalsAndKeepNullAsAStoppingValueWhenRemoved() = runComposeSwingTest {
        var includeFirst by mutableStateOf(true)
        var includeLater by mutableStateOf(true)
        val alias = styleLocalOf(FirstDescriptor)
        val firstStyle =
            SwingStyle<ProbeContainerScope> {
                InheritedFirst.provide("first")
                InheritedSecond.provide("nearer")
            }
        val laterStyle =
            SwingStyle<ProbeContainerScope> {
                alias.provide("later")
                InheritedSecond.provide(null)
            }
        val leaf = StyleProbe("leaf")
        setContent {
            ProbeContainer(StyleProbe("outer"), { InheritedSecond.provide("farther") }) {
                val firstModifier =
                    if (includeFirst) {
                        SwingModifier.styleable(PlainProbeContainer, firstStyle)
                    } else {
                        SwingModifier
                    }
                val laterModifier =
                    if (includeLater) {
                        SwingModifier.styleable(PlainProbeContainer, laterStyle)
                    } else {
                        SwingModifier
                    }
                val modifier =
                    SwingModifier
                        .then(firstModifier)
                        .then(laterModifier)
                SwingNode(factory = { StyleProbe("parent") }, modifier = modifier) {
                    ProbeLeaf(leaf, SwingStyle, PlainProbe)
                }
            }
        }

        onProbe("leaf")
            .assertProperty("later") { first }
            .assertProperty(PROBE_OWN_VALUE) { second }

        includeLater = false
        awaitIdle()
        onProbe("leaf")
            .assertProperty("first") { first }
            .assertProperty("nearer") { second }

        includeLater = true
        awaitIdle()
        includeFirst = false
        awaitIdle()
        onProbe("leaf")
            .assertProperty("later") { first }
            .assertProperty(PROBE_OWN_VALUE) { second }

        includeLater = false
        awaitIdle()
        onProbe("leaf")
            .assertProperty(PROBE_OWN_VALUE) { first }
            .assertProperty("farther") { second }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aNullBarrierStopsOtherLocalsSharingItsDescriptor(reverse: Boolean) = runComposeSwingTest {
        val alias = styleLocalOf(FirstDescriptor)
        val locals = if (reverse) listOf(alias, InheritedFirst) else listOf(InheritedFirst, alias)
        var barrier by mutableStateOf(true)
        setContent {
            ProbeContainer(StyleProbe(), { locals[0].provide("farther") }) {
                ProbeContainer(
                    StyleProbe("parent"),
                    {
                        locals[0].provide("nearer")
                        locals[1].provide(if (barrier) null else "last")
                    },
                ) {
                    ProbeLeaf(StyleProbe("leaf"), SwingStyle, PlainProbe)
                }
            }
        }
        onProbe("parent").assertProperty(null) { first }
        onProbe("leaf").assertProperty(PROBE_OWN_VALUE) { first }

        barrier = false
        awaitIdle()
        onProbe("parent").assertProperty("last") { first }
        onProbe("leaf").assertProperty("last") { first }

        barrier = true
        awaitIdle()
        onProbe("leaf").assertProperty(PROBE_OWN_VALUE) { first }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aNullBarrierStopsAFartherDeclarationThroughAnotherLocal(reverse: Boolean) = runComposeSwingTest {
        val alias = styleLocalOf(FirstDescriptor)
        val outer = if (reverse) alias else InheritedFirst
        val inner = if (reverse) InheritedFirst else alias
        setContent {
            ProbeContainer(StyleProbe(), { outer.provide("farther") }) {
                ProbeContainer(StyleProbe(), { inner.provide(null) }) {
                    ProbeLeaf(StyleProbe("leaf"), SwingStyle, PlainProbe)
                }
            }
        }
        onProbe("leaf").assertProperty(PROBE_OWN_VALUE) { first }
    }

    @Test
    fun aLocalSetToNullStopsInheritanceAtThatContainer() = runComposeSwingTest {
        val leaf = StyleProbe("leaf")
        setContent {
            ProbeContainer(StyleProbe(), { InheritedFirst.provide("farther") }) {
                ProbeLeaf(StyleProbe("sibling"), SwingStyle, PlainProbe)
                ProbeContainer(StyleProbe(), { InheritedFirst.provide(null) }) {
                    ProbeLeaf(leaf, SwingStyle, PlainProbe)
                }
            }
        }
        onProbe("sibling").assertProperty("farther") { first }
        onProbe("leaf").assertProperty(PROBE_OWN_VALUE, "below the null the component keeps its own value") { first }
        assertEquals(emptyList(), leaf.writes)
    }

    @Test
    fun aLocalIsNotAppliedToAComponentItsDescriptorDoesNotHandle() = runComposeSwingTest {
        setContent {
            ProbeContainer(StyleProbe(), { InheritedFirst.provide("inherited") }) {
                ProbeLeaf(StyleProbe("leaf"), SwingStyle, PlainProbe)
                SwingNode(
                    factory = { JLabel() },
                    modifier = SwingModifier.styleable(PlainProbe) { LabelText.provide("own style") },
                )
            }
        }
        onProbe("leaf").assertProperty("inherited") { first }
        onNodeOfType<JLabel>()
            .assertProperty("own style", "the label resolves its own style and skips a local it cannot carry") { text }
    }

    @Test
    fun aLocalIsNotInheritedWhereTheStyleSetsTheSamePropertyThroughAnotherStyleProperty() = runComposeSwingTest {
        setContent {
            ProbeContainer(StyleProbe(), { InheritedFirst.provide("inherited") }) {
                ProbeLeaf(StyleProbe("leaf"), { ProbeFirst.provide("own style") }, PlainProbe)
            }
        }
        onProbe("leaf").assertProperty("own style") { first }
    }

    @Test
    fun aLocalTheCallerStopsSettingTakesTheInheritedValueInTheSameResolve() = runComposeSwingTest {
        val leaf = StyleProbe("leaf")
        var setsOwn by mutableStateOf(true)
        val style = SwingStyle<ProbeScope> { if (setsOwn) InheritedFirst.provide("caller") }
        setContent {
            ProbeContainer(StyleProbe(), { InheritedFirst.provide("inherited") }) {
                ProbeLeaf(leaf, style, PlainProbe)
            }
        }
        onProbe("leaf").assertProperty("caller") { first }
        leaf.writes.clear()

        setsOwn = false
        awaitIdle()
        assertEquals(
            listOf("first=inherited"),
            leaf.writes,
            "the inherited value is written over the caller's, with no restore of the component's own between",
        )
    }

    @ParameterizedTest
    @MethodSource("localRefreshes")
    fun tooltipRefreshKeepsTheLastDeclaration(case: LocalRefreshCase) = runComposeSwingTest {
        var value by mutableStateOf("first")
        val style = SwingStyle<ProbeScope> { LabelToolTip.provide(LocalProbe.currentValue) }
        setContent {
            CompositionLocalProvider(LocalProbe provides value) {
                SwingNode(
                    factory = { JLabel("target") },
                    modifier = case.declare(SwingModifier.styleable(PlainProbe, style), directTip),
                )
            }
        }
        onNodeOfType<JLabel>().assertProperty(case.initialTooltip) { toolTipText }

        value = "second"
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(case.updatedTooltip) { toolTipText }
    }

    sealed class LocalRefreshCase(
        val initialTooltip: String,
        val updatedTooltip: String,
        val declare: (SwingModifier, ComponentPropertyDescriptor<JLabel, String?>) -> SwingModifier,
    ) {
        data object WithDirectDeclaration : LocalRefreshCase(
            "direct",
            "direct",
            { modifier, descriptor -> modifier.property(descriptor, "direct") },
        )

        data object StyleOnly : LocalRefreshCase("first", "second", { modifier, _ -> modifier })
    }

    companion object {
        @JvmStatic
        fun localRefreshes(): List<LocalRefreshCase> = listOf(
            LocalRefreshCase.WithDirectDeclaration,
            LocalRefreshCase.StyleOnly,
        )
    }

    private val directTip =
        ComponentPropertyDescriptor<JLabel, String?>(
            name = "toolTipText",
            read = { it.toolTipText },
            write = { label, value -> label.toolTipText = value },
        )

    @Test
    fun foregroundFontAndOrientationSetOnAContainerReachLabelsButtonsAndTextFieldsBelowIt() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        setContent {
            Panel(
                style = {
                    if (styled) {
                        foreground(Color.RED)
                        font(Font(Font.SANS_SERIF, Font.BOLD, 17))
                        componentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                    }
                },
            ) {
                Label("label", modifier = SwingModifier.testTag("label"))
                Panel {
                    Button("button", onClick = {}, modifier = SwingModifier.testTag("button"))
                    TextField("field", onValueChange = {}, modifier = SwingModifier.testTag("field"))
                }
            }
        }
        val own = listOf(JLabel(), JButton(), JTextField()).map { it.locals() }
        val reached = listOf("label", "button", "field")
        reached.forEach { tag ->
            onNodeWithTag(tag).assertProperty(
                listOf(Color.RED, Font(Font.SANS_SERIF, Font.BOLD, 17), ComponentOrientation.RIGHT_TO_LEFT),
                "every styled descendant at any depth takes the container's locals",
            ) { locals() }
        }

        styled = false
        awaitIdle()
        reached.zip(own).forEach { (tag, ownLocals) ->
            onNodeWithTag(tag).assertProperty(
                ownLocals,
                "a container that stops setting them gives each descendant back its own",
            ) { locals() }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun nearestLocalWinsAndNullStopsInheritance(initiallyBlocked: Boolean) = runComposeSwingTest {
        var blocked by mutableStateOf(initiallyBlocked)
        setContent {
            Panel(style = {
                foreground(Color.RED)
                font(Font(Font.SANS_SERIF, Font.BOLD, 17))
            }) {
                Label("sibling")
                Panel(style = {
                    foreground(if (blocked) null else Color.BLUE)
                    if (blocked) font(null)
                }) { Label("label") }
            }
        }
        if (!initiallyBlocked) {
            onNodeWithText("label")
                .assertProperty(Color.BLUE) { foreground }
                .assertProperty(Font(Font.SANS_SERIF, Font.BOLD, 17)) { font }
            blocked = true
            awaitIdle()
        }
        onNodeWithText("sibling")
            .assertProperty(Color.RED) { foreground }
            .assertProperty(Font(Font.SANS_SERIF, Font.BOLD, 17)) { font }
        val own = JLabel()
        onNodeWithText("label")
            .assertProperty(own.foreground) { foreground }
            .assertProperty(own.font) { font }
    }
}

private fun Component.locals(): List<Any?> = listOf(foreground, font, componentOrientation)
