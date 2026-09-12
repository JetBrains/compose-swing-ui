package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.RawParentProtocol
import org.jetbrains.compose.swing.modifier.layout.slot
import java.awt.Component
import java.awt.Container
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModifierPartitionTest {
    @Test
    fun parentLayoutElementsAreEmptyWithoutDeclarationsAndResolveSameKeyLastWins() {
        val partition = ModifierPartition()
        SwingModifier.testTag("tag").foldIn(partition) { current, element ->
            current.take(element)
            current
        }
        assertTrue(partition.parentLayoutElements.isEmpty())

        val first = TestParentLayoutElement("first")
        val second = TestParentLayoutElement("second")
        val modifier = SwingModifier then first then second
        modifier.foldIn(partition) { current, element ->
            current.take(element)
            current
        }

        assertEquals(listOf(second), partition.parentLayoutElements)
    }

    @Test
    fun additiveParentLayoutElementsAreRetainedInDeclarationOrder() {
        val partition = ModifierPartition()
        val first = AdditiveParentLayoutElement("first")
        val second = AdditiveParentLayoutElement("second")

        (SwingModifier then first then second).foldIn(
            partition,
        ) { current, element -> current.also { it.take(element) } }

        assertEquals(listOf(first, second), partition.parentLayoutElements)
    }

    @Test
    fun anElementCannotBeBothANodeAndAParentLayoutDeclaration() {
        val refusal =
            assertFailsWith<IllegalArgumentException> {
                ModifierPartition().take(AmbiguousElement)
            }

        assertTrue(refusal.message.orEmpty().contains("cannot both"))
    }

    @Test
    fun parentDataResolvesLastWinsAndAdditiveDeclarationsBeforeFolding() {
        val partition = ModifierPartition()
        val modifier =
            SwingModifier then
                AppendParentData("first", key = "shared") then
                AppendParentData("fourth", key = "subscription", additive = true) then
                AppendParentData("second", key = "shared") then
                AppendParentData("third", key = "other") then
                AppendParentData("fifth", key = "subscription", additive = true)

        modifier.foldIn(partition) { current, element -> current.also { it.take(element) } }

        assertEquals(listOf("fourth", "second", "third", "fifth"), partition.parentData)
    }

    @Test
    fun parentDataFromDifferentFamiliesIsRefusedRegardlessOfOrder() {
        listOf(
            SwingModifier then AppendParentData("first", parentProtocol = FirstProtocol) then
                AppendParentData("second", parentProtocol = SecondProtocol),
            SwingModifier then AppendParentData("second", parentProtocol = SecondProtocol) then
                AppendParentData("first", parentProtocol = FirstProtocol),
        ).forEach { modifier ->
            val partition = ModifierPartition()
            modifier.foldIn(partition) { current, element -> current.also { it.take(element) } }
            assertFailsWith<IllegalArgumentException> { partition.parentData }
        }
    }

    @Test
    fun equalButDistinctParentProtocolsAreDifferentFamilies() {
        val partition = ModifierPartition()
        (
            SwingModifier then
                AppendParentData("first", parentProtocol = EqualParentProtocol("same")) then
                AppendParentData("second", parentProtocol = EqualParentProtocol("same"))
        ).foldIn(partition) { current, element ->
            current.also { it.take(element) }
        }

        assertFailsWith<IllegalArgumentException> { partition.parentData }
    }

    @Test
    fun slotsAndLayoutDeclarationsShareKeyedResolution() {
        val attachment = SlotAttachment { _, _, _ -> {} }
        val partition = ModifierPartition()
        (
            SwingModifier
                .slot(RawParentProtocol, "first", attachment)
                .slot(RawParentProtocol, "second", attachment)
                .then(TestParentLayoutElement("layout"))
        ).foldIn(partition) { current, element ->
            current.also { it.take(element) }
        }

        assertEquals("second", partition.slot?.regionName)
        assertEquals(listOf(partition.slot, TestParentLayoutElement("layout")), partition.parentDeclarations)
    }
}

private data class TestParentLayoutElement(
    override val name: String,
) : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = TestParentProtocol
}

private data class AdditiveParentLayoutElement(
    override val name: String,
) : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = TestParentProtocol

    override val additive: Boolean get() = true
}

private data object AmbiguousElement :
    SwingModifier.NodeElement<Component, SwingModifier.Node<Component>>(),
    ParentLayoutElement {
    override val targetType: Class<Component> get() = Component::class.java

    override val key: Any get() = javaClass

    override val additive: Boolean get() = false

    override val parentProtocol: ParentProtocol get() = TestParentProtocol

    override fun create(): SwingModifier.Node<Component> = SwingModifier.Node()

    override fun update(node: SwingModifier.Node<Component>): Unit = Unit
}

private data class AppendParentData(
    val value: String,
    override val key: Any = value,
    override val additive: Boolean = false,
    override val parentProtocol: ParentProtocol = FirstProtocol,
) : ParentDataModifier {
    override fun modifyParentData(parentData: Any?): Any =
        (parentData as? List<*>)?.filterIsInstance<String>().orEmpty() + value
}

private data object FirstProtocol : ParentProtocol {
    override val description: String get() = "first test parent data"

    override fun accepts(parent: Container): Boolean = true
}

private data object SecondProtocol : ParentProtocol {
    override val description: String get() = "second test parent data"

    override fun accepts(parent: Container): Boolean = true
}

private data object TestParentProtocol : ParentProtocol {
    override val description: String get() = "test parent"

    override fun accepts(parent: Container): Boolean = true
}

private data class EqualParentProtocol(
    override val description: String,
) : ParentProtocol {
    override fun accepts(parent: Container): Boolean = true
}
