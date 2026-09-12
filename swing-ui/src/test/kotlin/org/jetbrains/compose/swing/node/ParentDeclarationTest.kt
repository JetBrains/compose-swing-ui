package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.RawParentProtocol
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.LayoutManager
import java.awt.LayoutManager2
import javax.swing.JButton
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
    @Test
    fun attachmentAndChangesDeclareConstraintAndElementsAtomicallyInOrder() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val child = SwingNodeHolder(JButton("child"))
        val first = TestParentLayoutElement("first")
        val second = TestParentLayoutElement("second")

        child.declaration.applyComponentLayout("first constraint", RawParentProtocol, listOf(first, second))
        root.add(child.component)
        child.declaration.attachedUnder(root)

        assertEquals(
            ComponentLayout(child.component, "first constraint", listOf(first, second)),
            layout.declarations.last(),
        )
        assertEquals(1, layout.declarations.size, "attachment declares the complete initial state once")
        assertEquals(0, layout.removedComponents, "capable layouts retain the attached child")

        child.declaration.applyComponentLayout("second constraint", RawParentProtocol, listOf(first, second))

        assertEquals(
            ComponentLayout(child.component, "second constraint", listOf(first, second)),
            layout.declarations.last(),
        )
        assertEquals(2, layout.declarations.size, "a parent-data change must make one atomic call")

        child.declaration.applyComponentLayout("second constraint", RawParentProtocol, listOf(second, first))

        assertEquals(
            ComponentLayout(child.component, "second constraint", listOf(second, first)),
            layout.declarations.last(),
        )
        assertEquals(3, layout.declarations.size, "an element change must make one atomic call")
        assertEquals(0, layout.removedComponents, "an update must not re-register the component")
    }

    @Test
    fun applierDefersACapableParentsRawConstraintUntilItsAtomicDeclaration() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val owner = TestCompositionOwner.observing()
        val applier = SwingApplier(SwingNodeHolder(root).attachedTo(owner))
        val child = SwingNodeHolder(JButton("child"))
        val element = TestParentLayoutElement("padding")
        child.declaration.applyComponentLayout("constraint", RawParentProtocol, listOf(element))

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()

            assertEquals(listOf<Any?>(null), layout.constraintsAtAdd)
            assertEquals(ComponentLayout(child.component, "constraint", listOf(element)), layout.declarations.single())
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun modifierDiffCarriesParentDataAndMeasuringElementsInOneAtomicDeclaration() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val owner = TestCompositionOwner.observing()
        val applier = SwingApplier(SwingNodeHolder(root).attachedTo(owner))
        val child: SwingNodeHolder<Component> = SwingNodeHolder(JButton("child"))
        val padding = TestParentLayoutElement("padding")
        child.applyModifierDiff(SwingModifier.layoutConstraint("constraint") then padding)

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()

            assertEquals(ComponentLayout(child.component, "constraint", listOf(padding)), layout.declarations.single())
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

        child.declaration.applyComponentLayout("constraint", RawParentProtocol, declaration)
        val calls = layout.declarations.size
        child.declaration.applyComponentLayout("constraint", RawParentProtocol, declaration)

        assertEquals(calls, layout.declarations.size)
    }

    @Test
    fun parentLayoutElementsAreCopiedAtTheDeclarationBoundary() {
        val layout = RecordingMeasurementLayout()
        val child = attached(JPanel(layout))
        val elements = mutableListOf<ParentLayoutElement>(TestParentLayoutElement("padding"))

        child.declaration.applyComponentLayout("constraint", RawParentProtocol, elements)
        elements.clear()

        assertEquals(listOf(TestParentLayoutElement("padding")), child.declaration.parentLayoutElements)
    }

    @Test
    fun initialParentLayoutElementsUnderAnUnsupportedParentAreRefusedOnAttachment() {
        val root = JPanel(BorderLayout())
        val child = SwingNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout(null, null, listOf(TestParentLayoutElement("padding")))
        root.add(child.component)

        val refusal = assertFailsWith<IllegalStateException> { child.declaration.attachedUnder(root) }

        assertTrue(refusal.message.orEmpty().contains("test measurement parent"))
    }

    @Test
    fun changedParentLayoutElementsUnderAnUnsupportedParentAreRefused() {
        val root = JPanel(BorderLayout())
        val child = attached(root)

        val refusal =
            assertFailsWith<IllegalStateException> {
                child.declaration.applyComponentLayout(null, null, listOf(TestParentLayoutElement("padding")))
            }

        assertTrue(refusal.message.orEmpty().contains("test measurement parent"))
        assertTrue(child.declaration.parentLayoutElements.isEmpty(), "a rejected declaration must not be retained")
    }

    @Test
    fun conventionalLayoutManagersKeepTheirConstraintRegistrationPath() {
        val layout = RecordingLayoutManager()
        val root = JPanel(layout)
        val child = attached(root)

        child.declaration.applyComponentLayout("replacement", RawParentProtocol, emptyList())

        assertSame(child.component, layout.removedComponent)
        assertEquals("replacement", layout.constraintOf(child.component))
    }

    @Test
    fun legacyLayoutManagerIsReregisteredWhenParentDataIsNull() {
        val layout = RecordingLegacyLayoutManager()
        val child = attached(JPanel(layout))
        layout.lastName = "not null"

        child.declaration.applyComponentLayout(null, RawParentProtocol, emptyList())

        assertSame(child.component, layout.removedComponent)
        assertEquals(null, layout.lastName)
    }

    @Test
    fun applierRefusesWrongTypedParentDataBeforeItAddsTheChild() {
        val root = JPanel(FlowLayout())
        val owner = TestCompositionOwner.observing()
        val applier = SwingApplier(SwingNodeHolder(root).attachedTo(owner))
        val child = SwingNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout("region", BorderOnlyParentData, emptyList())

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
        val owner = TestCompositionOwner.observing()
        val applier = SwingApplier(SwingNodeHolder(root).attachedTo(owner))
        val child = SwingNodeHolder(JButton("child"))
        var installs = 0
        val scrollPaneSlot = parentProtocolOf("JScrollPane slot") { it is JScrollPane }
        child.applyModifierDiff(
            SwingModifier.slot(
                scrollPaneSlot,
                "viewport",
                SlotAttachment { _, _, _ ->
                    installs++
                    {}
                },
            ),
        )
        applier.root.childPlacement = ChildPlacement.Slots("viewport")

        try {
            applier.onBeginChanges()
            applier.insertTopDown(0, child)
            applier.down(applier.root)
            val refusal = assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, child) }
            applier.up()

            assertTrue(refusal.message.orEmpty().contains("JScrollPane slot"))
            assertEquals(0, installs)
            assertEquals(0, root.componentCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun rawParentDataCanAttachToALayeredPaneWithoutALayoutManager() {
        val root = JLayeredPane()
        val owner = TestCompositionOwner.observing()
        val applier = SwingApplier(SwingNodeHolder(root).attachedTo(owner))
        val child = SwingNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout(200, RawParentProtocol, emptyList())

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

    private fun attached(root: JPanel): SwingNodeHolder<JButton> {
        val child = SwingNodeHolder(JButton("child"))
        root.add(child.component)
        child.declaration.attachedUnder(root)
        return child
    }
}

private data class TestParentLayoutElement(
    override val name: String,
) : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol
}

private val TestMeasurementParentProtocol = MeasurementLayoutManager.parentProtocol("test measurement parent")

private data object BorderOnlyParentData : ParentProtocol {
    override val description: String get() = "BorderLayout parent data"

    override fun accepts(parent: Container): Boolean = parent.layout is BorderLayout
}

private data class ComponentLayout(
    val component: Component,
    val constraint: Any?,
    val elements: List<ParentLayoutElement>,
)

private class RecordingMeasurementLayout :
    LayoutManager2,
    MeasurementLayoutManager {
    val declarations = mutableListOf<ComponentLayout>()

    val constraintsAtAdd = mutableListOf<Any?>()

    var removedComponents: Int = 0
        private set

    override fun declareComponentLayout(
        component: Component,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) {
        declarations += ComponentLayout(component, parentData, elements)
    }

    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        constraintsAtAdd += constraints
    }

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ): Unit = Unit

    override fun removeLayoutComponent(component: Component) {
        removedComponents++
    }

    override fun preferredLayoutSize(parent: Container): Dimension = Dimension()

    override fun minimumLayoutSize(parent: Container): Dimension = Dimension()

    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun getLayoutAlignmentX(target: Container): Float = 0.5f

    override fun getLayoutAlignmentY(target: Container): Float = 0.5f

    override fun invalidateLayout(target: Container): Unit = Unit

    override fun layoutContainer(parent: Container): Unit = Unit
}

private class RecordingLayoutManager : LayoutManager2 {
    private val constraints = mutableMapOf<Component, Any?>()

    var removedComponent: Component? = null
        private set

    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        this.constraints[component] = constraints
    }

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ): Unit = Unit

    override fun removeLayoutComponent(component: Component) {
        removedComponent = component
    }

    fun constraintOf(component: Component): Any? = constraints[component]

    override fun preferredLayoutSize(parent: Container): Dimension = Dimension()

    override fun minimumLayoutSize(parent: Container): Dimension = Dimension()

    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun getLayoutAlignmentX(target: Container): Float = 0.5f

    override fun getLayoutAlignmentY(target: Container): Float = 0.5f

    override fun invalidateLayout(target: Container): Unit = Unit

    override fun layoutContainer(parent: Container): Unit = Unit
}

private class RecordingLegacyLayoutManager : LayoutManager {
    var removedComponent: Component? = null
        private set

    var lastName: String? = null

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ) {
        lastName = name
    }

    override fun removeLayoutComponent(component: Component) {
        removedComponent = component
    }

    override fun preferredLayoutSize(parent: Container): Dimension = Dimension()

    override fun minimumLayoutSize(parent: Container): Dimension = Dimension()

    override fun layoutContainer(parent: Container): Unit = Unit
}
