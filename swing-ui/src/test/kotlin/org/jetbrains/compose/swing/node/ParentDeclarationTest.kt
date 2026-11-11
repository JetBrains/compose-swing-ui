// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.jetbrains.compose.swing.LayoutDeclaration
import org.jetbrains.compose.swing.RecordingLayout
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.ResolvedParentDeclarations
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.RawParentProtocol
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.modifier.resolveParentDeclarations
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.LayoutManager
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Holds the parent-layout protocol to the core runtime behavior without depending on Foundation. */
class ParentDeclarationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("layoutNodeChanges")
    fun aModifierUpdatePublishesOnDetachAndAfterTheUpdate(
        case: String,
        initial: SwingModifier,
        updated: SwingModifier,
        expectedElementCounts: List<Int>,
        expectedParentData: List<Any?>,
    ) = runComposeSwingTest {
        val layout = RecordingMeasurementLayout()
        val parent = JPanel(layout)
        val child = JLabel("child")
        var modifier by mutableStateOf(initial)
        setContent {
            SwingNode(factory = { parent }, content = {
                SwingNode(factory = { child }, modifier = modifier)
            })
        }
        layout.declarations.clear()
        layout.removals.clear()

        modifier = updated
        awaitIdle()

        assertEquals(listOf(child), parent.components.toList(), case)
        assertEquals(
            expectedElementCounts,
            layout.declarations.map { it.elements.size },
            case,
        )
        assertEquals(expectedParentData, layout.declarations.map { it.parentData }, case)
        assertEquals(expectedParentData.last(), layout.parentDataOf(child), case)
        assertEquals(emptyList(), layout.removals, case)
    }

    @Test
    fun attachmentAndChangesDeclareConstraintAndElementsAtomicallyInOrder() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val child = CreatedNodeHolder(JButton("child"))
        val first = TestParentLayoutElement("first")
        val second = TestParentLayoutElement("second")

        child.declaration.applyComponentLayout(
            resolvedDeclaration("first constraint", RawParentProtocol, listOf(first, second)),
            listOf(first, second),
        )
        root.add(child.component)
        child.declaration.attachedUnder(root, child.declaration.checkAttachableUnder(root))

        assertEquals(
            LayoutDeclaration(child.component, "first constraint", listOf(first, second)),
            layout.declarations.last(),
        )
        assertEquals(1, layout.declarations.size, "attachment declares the complete initial state once")
        assertEquals(0, layout.removals.size, "capable layouts retain the attached child")

        child.declaration.applyComponentLayout(
            resolvedDeclaration("second constraint", RawParentProtocol, listOf(first, second)),
            listOf(first, second),
        )

        assertEquals(
            LayoutDeclaration(child.component, "second constraint", listOf(first, second)),
            layout.declarations.last(),
        )
        assertEquals(2, layout.declarations.size, "a parent-data change must make one atomic call")

        child.declaration.applyComponentLayout(
            resolvedDeclaration("second constraint", RawParentProtocol, listOf(second, first)),
            listOf(second, first),
        )

        assertEquals(
            LayoutDeclaration(child.component, "second constraint", listOf(second, first)),
            layout.declarations.last(),
        )
        assertEquals(3, layout.declarations.size, "an element change must make one atomic call")
        assertEquals(0, layout.removals.size, "an update must not re-register the component")
    }

    @Test
    fun applierDefersACapableParentsRawConstraintUntilItsAtomicDeclaration() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child = CreatedNodeHolder(JButton("child"))
        val element = TestParentLayoutElement("padding")
        child.declaration.applyComponentLayout(
            resolvedDeclaration("constraint", RawParentProtocol, listOf(element)),
            listOf(element),
        )

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()

            assertEquals(listOf<Any?>(null), layout.registrations.map { it.constraints })
            assertEquals(
                LayoutDeclaration(child.component, "constraint", listOf(element)),
                layout.declarations.single(),
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun modifierDiffCarriesParentDataAndMeasuringElementsInOneAtomicDeclaration() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child: SwingNodeHolder<Component> = CreatedNodeHolder(JButton("child"))
        val padding = TestParentLayoutElement("padding")
        child.applyModifierDiff(SwingModifier.layoutConstraint("constraint") then padding)

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()

            assertEquals(
                LayoutDeclaration(child.component, "constraint", listOf(padding)),
                layout.declarations.single(),
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun equalLayoutDeclarationDoesNotCallTheManagerAgain() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val child = attached(root)
        val declaration = listOf(TestParentLayoutElement("padding"))

        child.declaration.applyComponentLayout(
            resolvedDeclaration("constraint", RawParentProtocol, declaration),
            declaration,
        )
        val calls = layout.declarations.size
        child.declaration.applyComponentLayout(
            resolvedDeclaration("constraint", RawParentProtocol, declaration),
            declaration,
        )

        assertEquals(calls, layout.declarations.size)
    }

    @Test
    fun parentLayoutElementsAreCopiedAtTheDeclarationBoundary() {
        val layout = RecordingMeasurementLayout()
        val child = attached(JPanel(layout))
        val elements = mutableListOf<ParentLayoutElement>(TestParentLayoutElement("padding"))

        child.declaration.applyComponentLayout(
            resolvedDeclaration("constraint", RawParentProtocol, elements),
            elements,
        )
        elements.clear()

        assertEquals(listOf(TestParentLayoutElement("padding")), child.declaration.parentLayoutElements)
    }

    @Test
    fun initialParentLayoutElementsUnderAnUnsupportedParentAreRefusedOnAttachment() {
        val root = JPanel(BorderLayout())
        val child = CreatedNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout(
            resolvedDeclaration(null, null, listOf(TestParentLayoutElement("padding"))),
            listOf(TestParentLayoutElement("padding")),
        )
        root.add(child.component)

        assertFailsWith<IllegalStateException> { child.declaration.checkAttachableUnder(root) }
    }

    @Test
    fun changedParentLayoutElementsUnderAnUnsupportedParentAreRefused() {
        val root = JPanel(BorderLayout())
        val child = attached(root)

        assertFailsWith<IllegalStateException> {
            child.declaration.applyComponentLayout(
                resolvedDeclaration(null, null, listOf(TestParentLayoutElement("padding"))),
                listOf(TestParentLayoutElement("padding")),
            )
        }
        assertTrue(child.declaration.parentLayoutElements.isEmpty(), "a rejected declaration must not be retained")
    }

    @Test
    fun conventionalLayoutManagersKeepTheirConstraintRegistrationPath() {
        val layout = RecordingLayout()
        val root = JPanel(layout)
        val child = attached(root)

        child.declaration.applyComponentLayout(
            resolvedDeclaration("replacement", RawParentProtocol, emptyList()),
            emptyList(),
        )

        assertSame(child.component, layout.removals.single())
        assertEquals("replacement", layout.registrations.last().constraints)
    }

    @Test
    fun legacyLayoutManagerIsReregisteredWhenParentDataIsNull() {
        val layout = mockk<LayoutManager>(relaxed = true)
        val child = attached(JPanel(layout))

        child.declaration.applyComponentLayout(
            resolvedDeclaration(null, RawParentProtocol, emptyList()),
            emptyList(),
        )

        verifyOrder {
            layout.removeLayoutComponent(child.component)
            layout.addLayoutComponent(null, child.component)
        }
    }

    @Test
    fun applierRefusesWrongTypedParentDataBeforeItAddsTheChild() {
        val root = JPanel(FlowLayout())
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child = CreatedNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout(
            resolvedDeclaration("region", BorderOnlyParentData, emptyList()),
            emptyList(),
        )

        try {
            applier.onBeginChanges()
            applier.insertTopDown(0, child)
            applier.down(applier.root)
            assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, child) }
            applier.up()
            assertEquals(0, root.componentCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun applierRefusesWrongSlotFamilyBeforeItsAttachmentMutatesTheHost() {
        val root = JPanel()
        val owner = TestCompositionOwner()
        val rootHolder = CreatedNodeHolder(root).attachedTo(owner)
        val applier = SwingApplier(rootHolder)
        val child = CreatedNodeHolder(JButton("child"))
        val install = mockk<() -> Unit>(relaxed = true)
        val uninstall: () -> Unit = {}
        val scrollPaneSlot = parentProtocolOf("JScrollPane slot") { it is JScrollPane }
        child.applyModifierDiff(
            SwingModifier.slot(
                scrollPaneSlot,
                "viewport",
                SlotAttachment { _, _, _ ->
                    install()
                    uninstall
                },
            ),
        )
        rootHolder.childPlacement = ChildPlacement.Slots("viewport")

        try {
            applier.onBeginChanges()
            applier.insertTopDown(0, child)
            applier.down(applier.root)
            assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, child) }
            applier.up()

            verify(exactly = 0) { install() }
            assertEquals(0, root.componentCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun aHostThatCannotMeasureNamesTheLayoutElementDeclaredRatherThanTheNodeStandingForIt() {
        val root = JPanel(FlowLayout())
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child = CreatedNodeHolder(JButton("child")).attachedTo(owner)
        child.applyModifierDiff(SwingModifier.then(AnyHostLayoutNodeElement))

        try {
            applier.onBeginChanges()
            applier.insertTopDown(0, child)
            applier.down(applier.root)
            val refusal = assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, child) }
            applier.up()

            assertTrue(
                refusal.message.orEmpty().contains("declares SwingModifier.anyHostLayout()."),
                "the refusal must name the declared element, but was: ${refusal.message}",
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun rawParentDataCanAttachToALayeredPaneWithoutALayoutManager() {
        val root = JLayeredPane()
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child = CreatedNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout(
            resolvedDeclaration(200, RawParentProtocol, emptyList()),
            emptyList(),
        )

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()

            assertEquals(1, root.componentCount)
            assertEquals(200, root.getLayer(child.component))
        } finally {
            owner.dispose()
        }
    }

    companion object {
        @JvmStatic
        fun layoutNodeChanges(): List<Arguments> = listOf(
            Arguments.of(
                "update one layout node and remove another",
                SwingModifier then RecordingLayoutNodeElement("a", mutableListOf(), key = "first") then
                    RecordingLayoutNodeElement("b", mutableListOf(), key = "second"),
                SwingModifier then RecordingLayoutNodeElement("c", mutableListOf(), key = "first"),
                listOf(1, 1),
                listOf(null, null),
            ),
            Arguments.of(
                "remove all layout nodes",
                SwingModifier then RecordingLayoutNodeElement("a", mutableListOf()) then
                    RecordingLayoutNodeElement("b", mutableListOf()),
                SwingModifier,
                listOf(1, 0),
                listOf(null, null),
            ),
            Arguments.of(
                "remove a layout node and replace the parent data",
                SwingModifier then NodeMark("own") then Data("old", 1, MeasuringProtocol),
                SwingModifier then Data("new", 1, MeasuringProtocol),
                listOf(0, 0),
                listOf(listOf("old"), listOf("new")),
            ),
            Arguments.of(
                "replace a layout node type and update another",
                SwingModifier then ChainLayoutNodeElement("first", mutableListOf(), mutableListOf()) then
                    RecordingLayoutNodeElement("a", mutableListOf(), key = "second"),
                SwingModifier then RecordingLayoutNodeElement("first", mutableListOf()) then
                    RecordingLayoutNodeElement("c", mutableListOf(), key = "second"),
                listOf(1, 2),
                listOf(null, null),
            ),
        )
    }

    private fun resolvedDeclaration(
        data: Any?,
        protocol: ParentProtocol?,
        elements: List<ParentLayoutElement>,
    ): ResolvedParentDeclarations = resolveParentDeclarations(
        buildList {
            if (protocol != null) add(TestParentData(data, protocol))
            addAll(elements)
        },
    )

    private data class TestParentData(
        val data: Any?,
        override val parentProtocol: ParentProtocol,
    ) : ParentDataModifier {
        override fun modifyParentData(parentData: Any?): Any? = data
    }

    private fun attached(root: JPanel): SwingNodeHolder<JButton> {
        val child = CreatedNodeHolder(JButton("child"))
        root.add(child.component)
        child.declaration.attachedUnder(root, child.declaration.checkAttachableUnder(root))
        return child
    }
}

/** [component]'s holder, attached to [owner] under a parent measuring with [layout]. */
internal fun <T : Component> attachedChild(
    owner: TestCompositionOwner,
    component: T,
    layout: MeasurementLayoutManager = RecordingMeasurementLayout(),
): SwingNodeHolder<T> {
    val root = JPanel(layout)
    val child = CreatedNodeHolder(component).attachedTo(owner)
    root.add(child.component)
    child.declaration.attachedUnder(root, child.declaration.checkAttachableUnder(root))
    return child
}

internal class RecordingLayoutNodeElement(
    private val value: String,
    private val events: MutableList<String>,
    /** Keyed by value unless given a key, so two declared together hold two slots. */
    override val key: Any = value,
    private val onAttach: (RecordingLayoutNode) -> Unit = {},
) : ParentLayoutNodeElement<RecordingLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): RecordingLayoutNode {
        events += "create($value)"
        return RecordingLayoutNode(events, onAttach)
    }

    override fun update(node: RecordingLayoutNode) {
        events += "update($value)"
    }

    override fun equals(other: Any?): Boolean = other is RecordingLayoutNodeElement && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

internal class RecordingLayoutNode(
    private val events: MutableList<String>,
    private val onAttachHook: (RecordingLayoutNode) -> Unit,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun onAttach() {
        events += "onAttach"
        onAttachHook(this)
    }

    override fun onDetach() {
        events += "onDetach"
    }

    override fun onReset() {
        events += "onReset"
    }
}

/** A chain member recording, at each lifecycle step, how many nodes of its own chain are attached. */
internal class ChainLayoutNodeElement(
    private val label: String,
    private val chain: MutableList<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : ParentLayoutNodeElement<ChainLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    /** Keyed by label, so two declared together hold two slots. */
    override val key: Any get() = label

    override fun create(): ChainLayoutNode = ChainLayoutNode(label, chain, sightings).also { chain += it }

    override fun update(node: ChainLayoutNode) = Unit

    override fun equals(other: Any?): Boolean = other is ChainLayoutNodeElement && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

internal class ChainLayoutNode(
    private val label: String,
    private val chain: List<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun onAttach() = sight("onAttach")

    override fun onReset() = sight("onReset")

    override fun onDetach() = sight("onDetach")

    private fun sight(step: String) {
        sightings += "$step $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }
}

/** A layout node element whose protocol accepts any host, named apart from the class of the node it creates. */
private data object AnyHostLayoutNodeElement : ParentLayoutNodeElement<AnyHostLayoutNode>() {
    override val name: String get() = "anyHostLayout"

    override val parentProtocol: ParentProtocol = parentProtocolOf("any host") { true }

    override fun create(): AnyHostLayoutNode = AnyHostLayoutNode()

    override fun update(node: AnyHostLayoutNode) = Unit
}

private class AnyHostLayoutNode : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = AnyHostLayoutNodeElement.parentProtocol
}

internal data class TestParentLayoutElement(
    override val name: String,
) : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override val key: Any get() = name
}

internal val TestMeasurementParentProtocol = MeasurementLayoutManager.parentProtocol("test measurement parent")

private data object BorderOnlyParentData : ParentProtocol {
    override val description: String get() = "BorderLayout parent data"

    override fun accepts(parent: Container): Boolean = parent.layout is BorderLayout
}
