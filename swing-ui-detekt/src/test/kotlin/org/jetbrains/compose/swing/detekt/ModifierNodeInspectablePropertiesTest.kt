package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModifierNodeInspectablePropertiesTest {
    private fun lint(source: String) =
        ModifierNodeInspectableProperties(Config.empty).lint(sourceWithLibrarySwingModifierImport(source))

    @Test
    fun `reports an element base subclass that overrides neither member`() {
        val findings =
            lint(
                """
                package sample

                private data class BorderElement(
                    private val spec: BorderSpec,
                ) : SwingModifier.NodeElement<JComponent, BorderElement.Node>() {
                    override val targetType: Class<JComponent> get() = JComponent::class.java
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("overrides neither `name` nor `declaredValues`"))
    }

    @TestFactory
    fun elementsMissingInspectablePropertiesAreReported(): List<DynamicTest> =
        invalidElements.map { (name, source) ->
            dynamicTest(name) {
                assertEquals(1, lint(source).size)
            }
        }

    @TestFactory
    fun validElementsAreNotReported(): List<DynamicTest> =
        validElements.map { (name, source) ->
            dynamicTest(name) {
                assertEquals(0, lint(source).size)
            }
        }

    private val invalidElements =
        listOf(
            "reports an element implementing the interface directly" to
                """
                package sample

                internal data class KeyElement(
                    val tokens: List<Any?>,
                ) : SwingModifier.Element,
                    SwingModifier.InspectableElement
                """.trimIndent(),
            "reports an element qualified by an alias of the library chain" to
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

                internal data class KeyElement(
                    val tokens: List<Any?>,
                ) : Chain.InspectableElement
                """.trimIndent(),
            "reports an element declaring name as a function rather than overriding the property" to
                """
                package sample

                private class ToolTipElement(private val text: String) :
                    SwingModifier.NodeElement<JComponent, Node>() {
                    fun name(): String = text
                }
                """.trimIndent(),
            "reports an element whose nested class is the one declaring a name" to
                """
                package sample

                private class ToolTipElement(private val text: String) :
                    SwingModifier.NodeElement<JComponent, ToolTipElement.Node>() {
                    class Node : SwingModifier.ComponentNode<JComponent>() {
                        val name: String = "toolTip"
                    }
                }
                """.trimIndent(),
        )

    private val validElements =
        listOf(
            "does not report an element built from nothing" to
                """
                package sample

                private object InitialFocusElement : SwingModifier.NodeElement<Component, Node>() {
                    override fun create(): Node = Node()
                }
                """.trimIndent(),
            "does not report an element carrying only a callback" to
                """
                package sample

                private class ClickElement(
                    private val onClick: () -> Unit,
                ) : SwingModifier.NodeElement<Component, Node>()
                """.trimIndent(),
            "does not report an unrelated type with the same simple name" to
                """
                package sample

                private class BorderElement(
                    private val width: Int,
                ) : Other.NodeElement<Component>()
                """.trimIndent(),
            "does not report an unrelated type with a doubly qualified SwingModifier" to
                """
                package sample

                private class BorderElement(
                    private val width: Int,
                ) : Other.SwingModifier.NodeElement<Component>()
                """.trimIndent(),
            "does not report a consumer SwingModifier NodeElement" to
                """
                package sample

                import consumer.SwingModifier

                private data class ItemElement(val value: String) : SwingModifier.NodeElement<ItemElement>()
                """.trimIndent(),
            "does not report an element that overrides only the name" to
                """
                package sample

                private object InitialFocusElement : SwingModifier.NodeElement<Component, Node>() {
                    override val name: String get() = "initialFocus"
                }
                """.trimIndent(),
            "does not report an element that overrides only the values it declares" to
                """
                package sample

                private data class BorderElement(
                    private val spec: BorderSpec,
                ) : SwingModifier.NodeElement<JComponent, BorderElement.Node>() {
                    override val declaredValues: Map<String, Any?> get() = mapOf("border" to spec)
                }
                """.trimIndent(),
            "does not report a shared seam taking its name as a constructor property" to
                """
                package sample

                internal open class PropertyElement<T, V>(
                    override val name: String,
                    private val value: V,
                ) : SwingModifier.NodeElement<T, PropertyNode<T, V>>()
                """.trimIndent(),
            "does not report an interface or an abstract class that leaves both to its implementors" to
                """
                package sample

                internal interface PlacementElement :
                    SwingModifier.Element,
                    SwingModifier.InspectableElement

                internal abstract class NamedElement(private val value: String) : SwingModifier.InspectableElement
                """.trimIndent(),
            "does not report a class inheriting the overrides from its own base" to
                """
                package sample

                internal open class BaseElement : SwingModifier.InspectableElement {
                    override val name: String get() = "base"
                }

                internal class InheritingElement : BaseElement()
                """.trimIndent(),
            "does not report a class that is not a modifier element" to
                """
                package sample

                internal data class BorderSpec(
                    val width: Int,
                ) : Comparable<BorderSpec> {
                    override fun compareTo(other: BorderSpec): Int = width - other.width
                }
                """.trimIndent(),
        )
}
