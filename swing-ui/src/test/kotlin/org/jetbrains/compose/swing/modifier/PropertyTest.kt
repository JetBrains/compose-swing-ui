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
            Label("target", modifier = SwingModifier.property(DeclaredNameProperty, DECLARED_TIP, rewriteOn = "name"))
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

    @Test
    fun anElementPutsBackAnOverwrittenPropertyAfterItDetaches() = runComposeSwingTest {
        val seen = ArrayList<String?>()
        var declared by mutableStateOf(false)
        setContent {
            Label("x", modifier = if (declared) SwingModifier.then(ToolTipElement("declared", seen)) else SwingModifier)
        }
        val label = onNodeOfType<JLabel>().fetch()
        label.toolTipText = "original"

        declared = true
        awaitIdle()
        assertEquals("declared", label.toolTipText, "the element writes the property it overwrites")

        declared = false
        awaitIdle()

        assertEquals(listOf<String?>("declared"), seen, "onDetach runs before the original is put back")
        assertEquals("original", label.toolTipText, "the property holds what it held before the element")
    }

    @Test
    fun anElementRewritingOnAPropertyDoesNotReapplyOverALaterOne() = runComposeSwingTest {
        val earlierUpdates = ArrayList<String>()
        setContent {
            Label(
                "x",
                modifier =
                    SwingModifier
                        .then(SharedWriter("earlier", earlierUpdates))
                        .then(SharedWriter("later", ArrayList())),
            )
        }
        val label = onNodeOfType<JLabel>().fetch()
        awaitIdle()

        assertEquals("later", label.getClientProperty(SHARED), "the later declaration stands")
        assertEquals(1, earlierUpdates.size, "the earlier declaration is written once, not again for the later write")

        label.putClientProperty(SHARED, "outside")

        assertEquals("later", label.getClientProperty(SHARED), "a change from outside leaves the later declaration")
        assertEquals(2, earlierUpdates.size, "the earlier declaration answers a change from outside once")
    }

    @Test
    fun anElementOverwritingAPropertyIsWrittenAgainWhenAnEarlierDeclarationOfItReplays() = runComposeSwingTest {
        setContent {
            Label(
                "x",
                modifier =
                    SwingModifier
                        .then(ReplayingToolTipElement("earlier"))
                        .then(ToolTipElement("later", ArrayList())),
            )
        }
        onNodeOfType<JLabel>().apply {
            assertProperty("later", "the later element's write stands over the earlier declaration") { toolTipText }
            fetch().name = "elsewhere"
        }
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(
            "later",
            "the replay of the earlier declaration is followed by the element that overwrites its property",
        ) { toolTipText }
    }

    @Test
    fun aDepartedElementOverwritingAPropertyLeavesTheDeclarationOfItStanding() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        setContent {
            Label(
                "x",
                modifier =
                    SwingModifier.property(ToolTipProperty, "modifier").styleable {
                        if (styled) ToolTipStyleProperty.provide("style")
                    },
            )
        }
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("style", "the style's element writes the property it overwrites") {
            toolTipText
        }

        styled = false
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty("modifier", "the declaration of the property stands once it leaves") {
            toolTipText
        }
    }

    @Test
    fun anElementWhoseOverwrittenPropertyThrowsOnPutBackIsReportedOnceAndALaterUpdateStillApplies() =
        runComposeSwingTest {
            var armed = false
            var declared by mutableStateOf<String?>(null)
            setContent {
                Label("x", modifier = declared?.let { SwingModifier.then(ClientWriter(it)) } ?: SwingModifier)
            }
            val label = onNodeOfType<JLabel>().fetch()
            label.addPropertyChangeListener(CLIENT) { if (armed) error("restore") }
            label.putClientProperty(CLIENT, "original")

            declared = "first"
            awaitIdle()
            assertEquals("first", label.getClientProperty(CLIENT))
            declared = null
            awaitIdle()
            assertEquals("original", label.getClientProperty(CLIENT), "the original is put back")

            declared = "declared"
            awaitIdle()
            armed = true
            declared = null
            assertEquals("restore", assertFailsWith<IllegalStateException> { awaitIdle() }.message, "reported once")
            assertEquals("original", label.getClientProperty(CLIENT), "the original is put back despite the failure")

            armed = false
            declared = "again"
            awaitIdle()
            assertEquals("again", label.getClientProperty(CLIENT), "a later update still applies")
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
        @JvmStatic
        fun propertyDeclarations(): List<Arguments> = listOf(
            Arguments.of("default restoration", SwingModifier.property(ToolTipProperty, DECLARED_TIP)),
            Arguments.of(
                "explicit restoration",
                SwingModifier.property(ToolTipProperty, DECLARED_TIP, RestorePolicy.EverythingWritten),
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

private const val SHARED = "shared"

private const val CLIENT = "client"

private class ClientWriter(
    private val value: String,
) : SwingModifier.NodeElement<JLabel, ClientWriter.Node>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override val overwrites: List<ComponentPropertyDescriptor<JLabel, *>> get() = listOf(ClientProperty)

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.component.putClientProperty(CLIENT, value)
    }

    override fun equals(other: Any?): Boolean = other is ClientWriter && other.value == value

    override fun hashCode(): Int = value.hashCode()

    class Node : SwingModifier.ComponentNode<JLabel>()
}

private val ClientProperty =
    ComponentPropertyDescriptor<JLabel, Any?>(
        CLIENT,
        read = { it.getClientProperty(CLIENT) },
        write = { label, value -> label.putClientProperty(CLIENT, value) },
    )

private class ToolTipElement(
    private val value: String,
    private val seen: MutableList<String?>,
) : SwingModifier.NodeElement<JLabel, ToolTipElement.Node>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override val overwrites: List<ComponentPropertyDescriptor<JLabel, *>> get() = listOf(ToolTipProperty)

    override fun create(): Node = Node(seen)

    override fun update(node: Node) {
        node.component.toolTipText = value
    }

    override fun equals(other: Any?): Boolean = other is ToolTipElement && other.value == value

    override fun hashCode(): Int = value.hashCode()

    class Node(
        private val seen: MutableList<String?>,
    ) : SwingModifier.ComponentNode<JLabel>() {
        override fun onDetach() {
            seen.add(component.toolTipText)
        }
    }
}

/** Writes the tooltip it overwrites and writes it again whenever the component's name changes. */
private class ReplayingToolTipElement(
    private val value: String,
) : SwingModifier.NodeElement<JLabel, ReplayingToolTipElement.Node>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override val overwrites: List<ComponentPropertyDescriptor<JLabel, *>> get() = listOf(ToolTipProperty)

    override val rewriteOn: List<String> get() = listOf("name")

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.component.toolTipText = value
    }

    override fun equals(other: Any?): Boolean = other is ReplayingToolTipElement && other.value == value

    override fun hashCode(): Int = value.hashCode()

    class Node : SwingModifier.ComponentNode<JLabel>()
}

private val ToolTipStyleProperty = stylePropertyOf<String> { ToolTipElement(it, ArrayList()) }

private class SharedWriter(
    private val tag: String,
    private val updates: MutableList<String>,
) : SwingModifier.NodeElement<JLabel, SharedWriter.Node>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override val key: Any get() = tag

    override val rewriteOn: List<String> get() = listOf(SHARED)

    override fun create(): Node = Node()

    override fun update(node: Node) {
        updates.add(tag)
        node.component.putClientProperty(SHARED, tag)
    }

    override fun equals(other: Any?): Boolean = other is SharedWriter && other.tag == tag

    override fun hashCode(): Int = tag.hashCode()

    class Node : SwingModifier.ComponentNode<JLabel>()
}
