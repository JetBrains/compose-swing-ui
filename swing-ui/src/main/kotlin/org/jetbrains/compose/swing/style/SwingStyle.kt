@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionLocal
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.PropertyElement
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * A [DslMarker] used to ensure that a member of an outer style scope does not resolve inside a nested block. Applied
 * to every style scope.
 */
@DslMarker
public annotation class SwingStyleMarker

/** The base interface for all style scopes, and the receiver of every style. */
@SwingStyleMarker
public interface SwingStyleScope

/**
 * The scope of a style for any component; every component's own style scope extends it. A style runs when the
 * component is applied, never in composition. A snapshot state it reads runs it again when that state changes.
 */
public interface ComponentStyleScope : SwingStyleScope {
    /**
     * Sets [value] for this property. The last write wins.
     *
     * @param value the value to write to this property.
     */
    public fun <V> SwingStyleProperty<V>.provide(value: V)

    /**
     * An extension property that accesses the current value of this composition local in the context of this scope:
     * where the styled component was declared, or for a nested style where its provider was declared. A change of a
     * dynamic local runs the style again.
     */
    public val <T> CompositionLocal<T>.currentValue: T

    /**
     * Runs every style the ancestors provided for [key], farthest first, against this scope. If no ancestor provides a
     * style for [key], calling this is equivalent to an empty style. A scope that is not a subtype of the key's scope
     * does not compile. Applying the same key recursively is prohibited.
     *
     * @param key the key whose nested styles run against this scope.
     */
    public fun <S : ComponentStyleScope> S.applyNestedStyle(key: NestedStyleKey<in S>)
}

/**
 * The scope of a component that holds children, which may provide a nested style that a styleable descendant
 * component reads and applies.
 */
public interface NestedStyleProviderScope : ComponentStyleScope {
    /**
     * Provides [style] to every descendant that applies [key] through [applyNestedStyle], at any depth. It does not
     * apply to this component. Several ancestors that provide one key all apply, farthest first, so the nearest wins
     * per property.
     *
     * The nested block executes against the consumer's scope and reads composition locals from its provider.
     * A provided nested block cannot publish another nested block. Every composition root starts a separate style
     * chain, including hosted compositions.
     *
     * @param key the key that selects the descendants the style applies to.
     * @param style the style each matching descendant runs.
     */
    public fun <S : ComponentStyleScope> provideNestedStyle(
        key: NestedStyleKey<S>,
        style: SwingStyle<S>,
    )
}

/**
 * A style: a block over the scope [S], semantically like a lambda executed on [S]. A property the style sets is
 * written onto the component, and a property it stops setting gets back the value the component held before the
 * style wrote it.
 *
 * A style must not synchronously resolve another component's style.
 * The companion is the empty style, which sets nothing.
 */
public fun interface SwingStyle<in S : SwingStyleScope> {
    /** Applies this style to the receiving scope. */
    public fun S.applyStyle()

    /** The empty style. */
    public companion object : SwingStyle<SwingStyleScope> {
        override fun SwingStyleScope.applyStyle() {}
    }
}

/**
 * Defines a style property whose value can be provided within a style scope. Its type names only the value; the
 * component it applies to is checked when the style is applied.
 *
 * Build one with [stylePropertyOf] or [styleLocalOf], once, as a top-level `val`.
 */
public class SwingStyleProperty<V> internal constructor(
    internal val declaration: (StyleCollector, V) -> Unit,
)

/**
 * Creates a style property with the restore and rewrite options of
 * [SwingModifier.property][org.jetbrains.compose.swing.modifier.property].
 * A style property and a modifier property with the same descriptor name share the original value to restore.
 *
 * @param descriptor the descriptor of the component property to set.
 * @param restores the restoration policy when the style stops setting the property.
 * @param rewriteOn the rewrite option of [SwingModifier.property][org.jetbrains.compose.swing.modifier.property];
 *   `null` by default.
 * @param alsoOverwrites the other properties affected by this write.
 * @return the style property for [descriptor].
 */
public fun <V> stylePropertyOf(
    descriptor: ComponentPropertyDescriptor<*, V>,
    restores: RestorePolicy = RestorePolicy.EverythingWritten,
    rewriteOn: String? = null,
    alsoOverwrites: List<ComponentPropertyDescriptor<*, *>> = emptyList(),
): SwingStyleProperty<V> =
    SwingStyleProperty { collector, value ->
        collector.declare(PropertyElement(descriptor, value, restores, inheritable = false, rewriteOn, alsoOverwrites))
    }

/**
 * Local style properties inherit their values down the component hierarchy. As [stylePropertyOf], for a property a
 * descendant inherits: a styled descendant that sets no value takes the value of the nearest styled ancestor that
 * set one, where [descriptor] handles the descendant's class. An ancestor that sets `null` stops the inheritance;
 * its descendants keep their own value. This holds for a nullable style local, since a non-null one such as
 * `componentOrientation` cannot be set to `null`. Locals with the same descriptor name share one property; the last
 * declaration wins for the component and its descendants.
 *
 * @param descriptor the descriptor of the inherited component property.
 * @param restores the values restored once the style stops setting the property, as in
 *   [SwingModifier.property][org.jetbrains.compose.swing.modifier.property].
 * @param rewriteOn the rewrite option of [SwingModifier.property][org.jetbrains.compose.swing.modifier.property];
 *   `null` by default.
 * @param alsoOverwrites the other properties written by this declaration, as in
 *   [SwingModifier.property][org.jetbrains.compose.swing.modifier.property].
 * @return the style property that sets the inherited property through [descriptor].
 */
public fun <V> styleLocalOf(
    descriptor: ComponentPropertyDescriptor<*, V>,
    restores: RestorePolicy = RestorePolicy.EverythingWritten,
    rewriteOn: String? = null,
    alsoOverwrites: List<ComponentPropertyDescriptor<*, *>> = emptyList(),
): SwingStyleProperty<V> {
    lateinit var property: SwingStyleProperty<V>
    property =
        SwingStyleProperty { collector, value ->
            val element = PropertyElement(descriptor, value, restores, inheritable = false, rewriteOn, alsoOverwrites)
            collector.declareLocal(element, ProvidedLocal(property, element.takeIf { value != null }))
        }
    return property
}

/**
 * Creates a style property whose value is applied by a custom element.
 * A declaration replaces an earlier one with the same key. The element's equality, target constraints
 * and restoration behavior apply as in a modifier.
 * A style writes its descriptor-backed properties before its custom elements.
 * Each group follows declaration order.
 *
 * [element] must return a [SwingModifier.NodeElement] or a [ParentLayoutElement] that folds no parent data.
 * Returning any other element causes the style to fail when it runs.
 * Additive elements are rejected; declare listeners in the component's modifier.
 * A layout element takes precedence over a modifier element with the same key.
 * An inheritable layout element is omitted when the parent's protocol refuses it.
 *
 * @param element builds the element that applies a value of this property.
 * @return the style property for [element].
 */
public fun <V> stylePropertyOf(element: (V) -> SwingModifier.Element): SwingStyleProperty<V> =
    SwingStyleProperty { collector, value -> collector.writeElement(element(value)) }

/**
 * Identifies the styles an ancestor provides for descendants of one kind. Identity is the instance.
 *
 * A style provided under the key runs against [S]; only a scope that is a subtype of [S] can apply the key.
 *
 * @param label the name the key shows in [toString].
 */
public class NestedStyleKey<S : ComponentStyleScope>(
    private val label: String,
) {
    override fun toString(): String = "NestedStyleKey($label)"
}
