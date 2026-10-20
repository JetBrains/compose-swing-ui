package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

private val LocalProbe = compositionLocalOf { "default" }

class StyleLocalTest {
    @Test
    fun theNearestAncestorSettingALocalWins() = runComposeSwingTest {
        setContent {
            ProbeContainer(
                StyleProbe(),
                {
                    InheritedFirst.provide("farther")
                    InheritedSecond.provide("farther")
                },
            ) {
                ProbeContainer(StyleProbe(), { InheritedFirst.provide("nearer") }) {
                    ProbeLeaf(StyleProbe("leaf"), SwingStyle, PlainProbe)
                }
            }
        }
        onProbe("leaf")
            .assertProperty("nearer") { first }
            .assertProperty("farther", "a local the nearer ancestor leaves unset comes from the farther one") {
                second
            }
    }

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
                    modifier = SwingModifier.styleable({ LabelText.provide("own style") }, PlainProbe),
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

    @Test
    fun aDirectDeclarationAfterTheStyleSurvivesALocalRefresh() = runComposeSwingTest {
        var value by mutableStateOf("first")
        val style = SwingStyle<ProbeScope> { LabelToolTip.provide(LocalProbe.currentValue) }
        setContent {
            CompositionLocalProvider(LocalProbe provides value) {
                SwingNode(
                    factory = { JLabel("target") },
                    modifier = SwingModifier.styleable(style, PlainProbe).property(directTip, "direct"),
                )
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty("direct", "before the change") { toolTipText }

        value = "second"
        awaitIdle()
        label.assertProperty("direct", "after the local changed, the later direct declaration should stand") {
            toolTipText
        }
    }

    @Test
    fun theStyleValueReachesTheTooltipThroughALocalRefresh() = runComposeSwingTest {
        var value by mutableStateOf("first")
        val style = SwingStyle<ProbeScope> { LabelToolTip.provide(LocalProbe.currentValue) }
        setContent {
            CompositionLocalProvider(LocalProbe provides value) {
                SwingNode(
                    factory = { JLabel("target") },
                    modifier = SwingModifier.styleable(style, PlainProbe),
                )
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty("first", "before the change") { toolTipText }

        value = "second"
        awaitIdle()
        label.assertProperty("second", "after the local changed, the style should rewrite the tooltip") {
            toolTipText
        }
    }

    private val directTip =
        ComponentPropertyDescriptor<JLabel, String?>(
            name = "toolTipText",
            read = { it.toolTipText },
            write = { label, value -> label.toolTipText = value },
        )
}
