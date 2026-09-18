package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
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

    @Test
    fun `reports an element implementing the interface directly`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                internal data class KeyElement(
                    val tokens: List<Any?>,
                ) : SwingModifier.Element,
                    SwingModifier.InspectableElement
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports an element qualified by an alias of the library chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

                internal data class KeyElement(
                    val tokens: List<Any?>,
                ) : Chain.InspectableElement
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports an element declaring name as a function rather than overriding the property`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                private class ToolTipElement(private val text: String) :
                    SwingModifier.NodeElement<JComponent, Node>() {
                    fun name(): String = text
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports an element whose nested class is the one declaring a name`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                private class ToolTipElement(private val text: String) :
                    SwingModifier.NodeElement<JComponent, ToolTipElement.Node>() {
                    class Node : SwingModifier.ComponentNode<JComponent>() {
                        val name: String = "toolTip"
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an element built from nothing`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private object InitialFocusElement : SwingModifier.NodeElement<Component, Node>() {
                    override fun create(): Node = Node()
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an element carrying only a callback`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private class ClickElement(
                    private val onClick: () -> Unit,
                ) : SwingModifier.NodeElement<Component, Node>()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an unrelated type with the same simple name`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private class BorderElement(
                    private val width: Int,
                ) : Other.NodeElement<Component>()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an unrelated type with a doubly qualified SwingModifier`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private class BorderElement(
                    private val width: Int,
                ) : Other.SwingModifier.NodeElement<Component>()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a consumer SwingModifier NodeElement`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import consumer.SwingModifier

                private data class ItemElement(val value: String) : SwingModifier.NodeElement<ItemElement>()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an element that overrides only the name`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private object InitialFocusElement : SwingModifier.NodeElement<Component, Node>() {
                    override val name: String get() = "initialFocus"
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an element that overrides only the values it declares`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private data class BorderElement(
                    private val spec: BorderSpec,
                ) : SwingModifier.NodeElement<JComponent, BorderElement.Node>() {
                    override val declaredValues: Map<String, Any?> get() = mapOf("border" to spec)
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a shared seam taking its name as a constructor property`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                internal open class PropertyElement<T, V>(
                    override val name: String,
                    private val value: V,
                ) : SwingModifier.NodeElement<T, PropertyNode<T, V>>()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an interface or an abstract class that leaves both to its implementors`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                internal interface PlacementElement :
                    SwingModifier.Element,
                    SwingModifier.InspectableElement

                internal abstract class NamedElement : SwingModifier.InspectableElement
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a class inheriting the overrides from its own base`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                internal open class BaseElement : SwingModifier.InspectableElement {
                    override val name: String get() = "base"
                }

                internal class InheritingElement : BaseElement()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a class that is not a modifier element`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                internal data class BorderSpec(
                    val width: Int,
                ) : Comparable<BorderSpec> {
                    override fun compareTo(other: BorderSpec): Int = width - other.width
                }
                """.trimIndent(),
            ).size,
        )
    }
}
