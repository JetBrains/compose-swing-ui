// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.stylePropertyOf
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * The public builder a custom component declares a Swing property through: it writes the declared value
 * while the declaration stands, and puts back what the component was found holding once it leaves.
 *
 * A caller states through `restores` whether the component is held to carrying that value again, and
 * may name a `rewriteOn` bean property whose announced change means the declared value was overwritten.
 * Where two declarations rewrite on one property, the one written last stands.
 */
class PropertyTest {
    @Test
    fun aStylePropertyAndAComponentModifierWritingOnePropertyAreHeldApartThroughRemovalAndRebind() =
        runComposeSwingTest {
            var styleColor by mutableStateOf<Color?>(Color.RED)
            var modifierColor by mutableStateOf<Color?>(null)
            setContent {
                Label(
                    "target",
                    modifier = modifierColor?.let { SwingModifier.property(BackgroundProperty, it) } ?: SwingModifier,
                    style = { styleColor?.let { background(it) } },
                )
            }
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty(Color.RED, "the style's color stands alone") { background }

            modifierColor = Color.BLUE
            awaitIdle()
            modifierColor = Color.GREEN
            awaitIdle()
            modifierColor = null
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty(
                Color.RED,
                "the modifier's removal puts back the style's color",
            ) { background }

            styleColor = Color.BLUE
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty(
                Color.BLUE,
                "a rebound style property writes its new color",
            ) { background }

            styleColor = null
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty(
                JLabel().background,
                "the style's removal puts back the color it found",
            ) { background }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("propertyDeclarations")
    fun aDeclaredPropertyIsWrittenAndHandedBackFromTheWidget(
        case: String,
        declaration: SwingModifier,
    ) = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JLabel("target").apply { toolTipText = WIDGET_TIP } },
                modifier = if (declared) declaration else SwingModifier,
            )
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(DECLARED_TIP, case) { toolTipText }

        declared = false
        awaitIdle()
        label.assertProperty(
            WIDGET_TIP,
            "withdrawing the declaration should hand back what the widget was carrying",
        ) { toolTipText }
    }

    @Test
    fun aDeclaredPropertyOnlyPolicyOwesBackOnlyItsOwnName() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = {
                    JLabel("target").apply {
                        toolTipText = WIDGET_TIP
                        name = "named by the widget"
                    }
                },
                modifier = if (declared) SwingModifier.declaredTipDerivingName(DECLARED_TIP) else SwingModifier,
            )
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(DECLARED_TIP, "the declared value should reach the widget") { toolTipText }

        declared = false
        // The write derives `name` from the tooltip too, the way a look and feel derives a property of
        // its own from a write. RestorePolicy.DeclaredPropertyOnly holds the departing slot to its own
        // name ("toolTipText") alone, so the restore check the harness runs does not fail even though
        // `name` is left standing at what the write derived rather than at what it carried before.
        awaitIdle()
        label.assertProperty(WIDGET_TIP, "the declared property is still put back") { toolTipText }
        label.assertProperty(
            "derived from $WIDGET_TIP",
            "a property the policy does not own is left as the write derived it, not restored",
        ) { name }
    }

    @Test
    fun rewriteOnReassertsTheDeclaredValueWhenTheNamedPropertyAnnouncesAChange() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JLabel("target") },
                modifier = SwingModifier.property(DeclaredNameProperty, DECLARED_TIP, rewriteOn = "name"),
            )
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(DECLARED_TIP, label.name, "the declared value should reach the widget")

        // Something outside the modifier overwrites the property and announces the change, the way a
        // look and feel does; the node listens for the property it declares to be announced - "name"
        // reliably fires a PropertyChangeEvent of that exact name, unlike "toolTipText", which Swing
        // announces under the differently-cased client-property key "ToolTipText" - and writes the
        // declared value again.
        label.name = "elsewhere"
        awaitIdle()
        assertEquals(
            DECLARED_TIP,
            label.name,
            "an external overwrite of a rewriteOn property is reasserted",
        )
    }

    @Test
    fun aRewriteOnDeclarationDoesNotReapplyOverALaterDeclarationThatOverwroteIt() = runComposeSwingTest {
        val earlier =
            ComponentPropertyDescriptor<JLabel, String?>(
                name = "earlier",
                read = { it.name },
                write = { label, value -> label.name = value },
            )
        val later =
            ComponentPropertyDescriptor<JLabel, String?>(
                name = "later",
                read = { it.name },
                write = { label, value -> label.name = value },
            )
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier
                        .property(earlier, "A", rewriteOn = "name")
                        .property(later, "B", rewriteOn = "name"),
            )
        }
        onNodeOfType<JLabel>().apply {
            assertProperty("B", "the later declaration's write stands over the earlier one") { name }
            fetch().name = "elsewhere"
        }
        awaitIdle()
        onNodeOfType<JLabel>()
            .assertProperty("B", "an outside overwrite is answered in declaration order, the later one last") { name }
    }

    @Test
    fun aRewriteOnDeclarationWhoseWriteAnnouncesItsOwnChangeDoesNotReapplyItself() = runComposeSwingTest {
        val appending =
            ComponentPropertyDescriptor<JLabel, String?>(
                name = "appending",
                read = { it.name },
                write = { label, value -> label.name = value?.let { label.name.orEmpty() + it } },
            )
        setContent {
            Label(
                "target",
                modifier = SwingModifier.property(appending, "x", rewriteOn = "name"),
            )
        }

        onNodeOfType<JLabel>()
            .assertProperty("x", "the declaration's own change event does not write it again") { name }
    }

    @Test
    fun aLaterDeclarationWithoutRewriteOnDoesNotWin() = runComposeSwingTest {
        val plainName =
            ComponentPropertyDescriptor<JLabel, String?>(
                name = "plainName",
                read = { it.name },
                write = { label, value -> label.name = value },
            )
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier
                        .property(DeclaredNameProperty, "named by the first", rewriteOn = "name")
                        .property(plainName, "named by the later"),
            )
        }
        onNodeOfType<JLabel>().apply {
            assertProperty(
                "named by the first",
                "the later write announces the change, which only the declaration naming rewriteOn answers",
            ) { name }
            fetch().name = "elsewhere"
        }
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(
            "named by the first",
            "a later declaration that names no rewriteOn does not win again after the announced change",
        ) { name }
    }

    @Test
    fun aRemovalWhoseRestoreOfTheDeclaredPropertyThrowsStillRestoresThePropertyItOverwrote() = runComposeSwingTest {
        val failure = IllegalStateException("The restore failed")
        val failingName =
            ComponentPropertyDescriptor<JLabel, String?>(
                name = "name",
                read = { it.name },
                write = { component, value ->
                    if (value == "own name") throw failure
                    component.name = value
                    component.toolTipText = value
                },
            )
        val tip =
            ComponentPropertyDescriptor<JLabel, String?>(
                name = "toolTipText",
                read = { it.toolTipText },
                write = { component, value -> component.toolTipText = value },
            )
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = {
                    JLabel().apply {
                        name = "own name"
                        toolTipText = "own tip"
                    }
                },
                modifier =
                    if (declared) {
                        SwingModifier.property(failingName, "outer", alsoOverwrites = listOf(tip))
                    } else {
                        SwingModifier
                    },
            )
        }
        onNodeOfType<JLabel>().assertProperty("outer", "the declaration writes the tooltip") { toolTipText }

        declared = false
        assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })

        onNodeOfType<JLabel>().assertProperty(
            "own tip",
            "the overwritten property is restored although the primary threw",
        ) { toolTipText }
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false")
    fun lastDeclarationWinsRewrites(
        twoStyles: Boolean,
        joinsLater: Boolean,
    ) = runComposeSwingTest {
        var joined by mutableStateOf(!joinsLater)
        val laterName = if (twoStyles) "named later by the style" else "named by the style"
        setContent {
            val first =
                if (joined && !twoStyles) {
                    SwingModifier.property(DeclaredNameProperty, "named by the modifier", rewriteOn = "name")
                } else {
                    SwingModifier
                }
            Label(
                "target",
                modifier =
                    first.styleable {
                        StyleNameProperty.provide("named by the style")
                        if (twoStyles) LaterStyleNameProperty.provide(laterName)
                    },
            )
        }
        joined = true
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty(laterName) { name }
            fetch().name = "elsewhere"
        }
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(laterName) { name }
    }

    companion object {
        @Suppress("DEPRECATION")
        @Deprecated("Covers the deprecated inheritable property overload.")
        @JvmStatic
        fun propertyDeclarations(): List<Arguments> = listOf(
            Arguments.of("default restoration", SwingModifier.property(ToolTipProperty, DECLARED_TIP)),
            Arguments.of(
                "explicit restoration",
                SwingModifier.property(ToolTipProperty, DECLARED_TIP, RestorePolicy.EverythingWritten),
            ),
            Arguments.of(
                "named inheritance with default restoration",
                SwingModifier.property(ToolTipProperty, DECLARED_TIP, inheritable = true),
            ),
            Arguments.of(
                "named non-inheritance with default restoration",
                SwingModifier.property(ToolTipProperty, DECLARED_TIP, inheritable = false),
            ),
            Arguments.of(
                "positional inheritance",
                SwingModifier.property(ToolTipProperty, DECLARED_TIP, RestorePolicy.EverythingWritten, true),
            ),
            Arguments.of(
                "named inheritance with rewrite",
                SwingModifier.property(ToolTipProperty, DECLARED_TIP, inheritable = true, rewriteOn = "toolTipText"),
            ),
        )
    }
}

/** A style property writing the component's name, rewriting it on every announced change of it. */
private val StyleNameProperty =
    stylePropertyOf(
        ComponentPropertyDescriptor<JLabel, String?>(
            name = "name",
            read = { it.name },
            write = { label, value -> label.name = value },
        ),
        rewriteOn = "name",
    )

/** A second style property as [StyleNameProperty], held in a slot of its own within the style. */
private val LaterStyleNameProperty =
    stylePropertyOf(
        ComponentPropertyDescriptor<JLabel, String?>(
            name = "laterStyleName",
            read = { it.name },
            write = { label, value -> label.name = value },
        ),
        rewriteOn = "name",
    )

/** The property [rewriteOnReassertsTheDeclaredValueWhenTheNamedPropertyAnnouncesAChange] declares and listens on. */
private val DeclaredNameProperty =
    ComponentPropertyDescriptor<JLabel, String?>(
        name = "name",
        read = { it.name },
        write = { label, value -> label.name = value },
    )

/**
 * The property both declarations here write through, built once so they share one slot, the way
 * [SwingModifier.property]'s documentation directs a custom component to declare a property.
 */
private val ToolTipProperty =
    ComponentPropertyDescriptor<JLabel, String?>(
        name = "toolTipText",
        read = { it.toolTipText },
        write = { label, value -> label.toolTipText = value },
    )

/**
 * Writes the tooltip and, standing in for a look and feel that derives a property of its own from a
 * write, derives the component's name from it too.
 */
private val ToolTipDerivingNameProperty =
    ComponentPropertyDescriptor<JLabel, String?>(
        name = "toolTipText",
        read = { it.toolTipText },
        write = { label, value ->
            label.toolTipText = value
            label.name = "derived from $value"
        },
    )

private fun SwingModifier.declaredTipDerivingName(tip: String): SwingModifier =
    property(ToolTipDerivingNameProperty, tip, restores = RestorePolicy.DeclaredPropertyOnly)

/** What the widget is built carrying, so a restore has something of the widget's own to hand back. */
private const val WIDGET_TIP = "carried by the widget"

private const val DECLARED_TIP = "named by the declaration"

internal val BackgroundProperty =
    ComponentPropertyDescriptor<JLabel, Color?>(
        "background",
        read = { if (it.isBackgroundSet) it.background else null },
        write = { label, color -> label.background = color },
    )
