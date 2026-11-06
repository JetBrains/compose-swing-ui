package org.jetbrains.compose.swing.style

import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import androidx.compose.runtime.CompositionLocal
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.modifier.PropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.styleNode

/**
 * Collects one resolve of a style node's style into a [StyleCollection], one element per property in write order, the
 * last write of a property winning, as in a modifier. One collector serves every node of a composition, one collect
 * at a time.
 *
 * The order is the kind's default style, then the caller's style, then each style local an ancestor provided
 * that neither set. The ancestors are the styled nodes the component is a descendant of, up to its composition's root,
 * and past it where a widget hosts that composition.
 *
 * A nested block runs against the consuming scope, with composition locals from its provider.
 */
internal class StyleCollector : NestedStyleProviderScope {
    /** The node whose style is being collected; `null` between collects. */
    private var node: StyleNode? = null

    /** What the running collect writes into; `null` between collects. */
    private var collected: Collected? = null

    /** A cleared collection a collect takes, so one owner allocates one for the collects that do not overlap. */
    private var spare: Collected? = null

    /** The nearest publisher above the component, where the walk for nested styles and locals starts. */
    private var publisher: SwingNodeHolder<*>? = null

    /** The nested keys running, innermost last. */
    private val blockKeys = ArrayList<NestedStyleKey<*>>()

    /** The provider whose composition locals the running nested style reads; otherwise `null`. */
    private var localProvider: StyleNode? = null

    /** Whether the default of a nesting kind is running, which may write only inside a nested block. */
    private var onlyNests = false

    /** Sets [onlyNests] and returns what it was, for the element to bracket its default style with. */
    private val setOnlyNests: (Boolean) -> Boolean = { value -> onlyNests.also { onlyNests = value } }

    /**
     * Collects [node]'s [element] with the publishers from [publisher] up, and returns what it wrote. The caller
     * [releases][StyleCollection.release] the result once it has read it, which may run other collects.
     */
    fun collect(
        node: StyleNode,
        element: StyleElement<*, *>,
        publisher: SwingNodeHolder<*>?,
    ): StyleCollection {
        check(this.node == null) {
            "A style collect started while the collect of another style node is running. " +
                "A style must not resolve another component's style."
        }
        val result = spare ?: Collected()
        spare = null
        this.node = node
        this.publisher = publisher
        collected = result
        try {
            val scope = element.execute(node.reusableScope(element), setOnlyNests) { scopeFor(node) }
            inheritLocals(checkNotNull(node.holder).component.javaClass)
            result.scope = scope
            return result
        } finally {
            this.node = null
            this.publisher = null
            collected = null
            blockKeys.clear()
            localProvider = null
        }
    }

    private fun scopeFor(owner: StyleNode): NestedStyleProviderScope =
        object : NestedStyleProviderScope by this {
            override val <T> CompositionLocal<T>.currentValue: T
                get() =
                    if (this@StyleCollector.node === owner) {
                        with(this@StyleCollector) { currentValue }
                    } else {
                        owner.localMap[this]
                    }
        }

    /**
     * Writes each local the nearest styled ancestor that set it provided, where the component's own style did not
     * set its property. A local set to `null` stops the walk for that property.
     */
    private fun inheritLocals(componentClass: Class<*>) {
        var stopped: Set<String>? = null
        var node = publisher
        while (node != null) {
            val locals = checkNotNull(node.styleNode).providedStyles.locals
            node = node.publisherAbove
            if (locals.isEmpty()) continue
            for ((key, local) in locals) {
                // A nearer ancestor or the component's own style already wrote it.
                if (
                    checkNotNull(collected) { "A style property was provided outside a collect" }.declarations.any {
                        it.key == key
                    } ||
                    stopped?.contains(key) == true
                ) {
                    continue
                }
                if (local.element == null) {
                    stopped = stopped.orEmpty() + key
                } else if (local.element.handles(componentClass)) {
                    declare(local.element)
                }
            }
        }
    }

    override fun <V> SwingStyleProperty<V>.provide(value: V) {
        check(!onlyNests || blockKeys.isNotEmpty()) {
            "The default of a nesting kind may only apply nested styles, but it provides a property outside one. " +
                "Build the kind with the SwingStyleKind constructor instead of nestingStyleKind."
        }
        provideTo(this@StyleCollector, value)
    }

    fun writeElement(element: SwingModifier.Element) = checkNotNull(collected).write(element)

    fun declare(element: PropertyElement<*, *>) {
        val declarations = checkNotNull(collected).declarations
        declarations.asMutableList().removeAll { it.key == element.key }
        declarations.add(element)
    }

    val locals: MutableMap<String, ProvidedLocal> get() = checkNotNull(collected).locals

    override val <T> CompositionLocal<T>.currentValue: T
        get() {
            val reading = checkNotNull(node) { "A composition local was read outside a collect" }
            val provider = localProvider
            if (provider == null) reading.readLocal()
            return if (provider == null) {
                checkNotNull(reading.holder) { "Node is not attached" }.compositionLocalMap[this]
            } else {
                provider.localMap[this]
            }
        }

    override fun <S : ComponentStyleScope> S.applyNestedStyle(key: NestedStyleKey<in S>) {
        this@StyleCollector.applyProvided(this, key)
    }

    /** Runs the styles the ancestors provide for [key] against [consumer], farthest first. */
    private fun applyProvided(
        consumer: ComponentStyleScope,
        key: NestedStyleKey<*>,
    ) {
        // The block would run the providers of key again, and so itself, without end.
        check(key !in blockKeys) {
            "applyNestedStyle($key) runs inside a provideNestedStyle block for $key, which it would run again " +
                "without end. Remove applyNestedStyle($key) from that block."
        }
        applyProvidedFrom(publisher, consumer, key)
    }

    /** Runs the styles [node] and the publishers above it provide for [key] against [consumer], farthest first. */
    private fun applyProvidedFrom(
        node: SwingNodeHolder<*>?,
        consumer: ComponentStyleScope,
        key: NestedStyleKey<*>,
    ) {
        if (node == null) return
        applyProvidedFrom(node.publisherAbove, consumer, key)
        val style = checkNotNull(node.styleNode)
        val styles = style.providedStyles.nested[key]?.value ?: return
        val outerProvider = localProvider
        blockKeys.add(key)
        localProvider = style
        try {
            for (provided in styles) with(provided) { consumer.applyStyle() }
        } finally {
            localProvider = outerProvider
            blockKeys.removeAt(blockKeys.lastIndex)
        }
    }

    override fun <S : ComponentStyleScope> provideNestedStyle(
        key: NestedStyleKey<S>,
        style: SwingStyle<S>,
    ) {
        check(blockKeys.isEmpty()) {
            "provideNestedStyle($key) cannot run inside a provideNestedStyle block. " +
                "Provide the inner key from the component's own style instead."
        }
        // applyNestedStyle accepts only a subtype of the invariant key's scope S.
        @Suppress("UNCHECKED_CAST")
        val provided = style as SwingStyle<ComponentStyleScope>
        checkNotNull(collected) { "A style property was provided outside a collect" }
            .nested
            .getOrPut(key) { ArrayList() }
            .add(provided)
    }

    /** The collection a collect of this collector writes into, recycled through [spare] once released. */
    private inner class Collected : StyleCollection {
        override val declarations = MutableObjectList<PropertyElement<*, *>>()

        override val properties: ObjectList<SwingModifier.Element> get() = propertyElements

        /** Property declarations mutated only by this collection. */
        private val propertyElements = MutableObjectList<SwingModifier.Element>()

        override val layoutElements: ObjectList<ParentLayoutElement> get() = declaredLayout

        /** Layout declarations mutated only by this collection. */
        private val declaredLayout = MutableObjectList<ParentLayoutElement>()

        override val locals = LinkedHashMap<String, ProvidedLocal>()

        override val nested = LinkedHashMap<NestedStyleKey<*>, MutableList<SwingStyle<ComponentStyleScope>>>()

        override var scope: ComponentStyleScope? = null

        /** Records [element] as the last write for its key; rejects additive elements and parent data modifiers. */
        fun write(element: SwingModifier.Element) {
            when {
                element is SwingModifier.NodeElement<*, *> -> {
                    require(!element.additive) {
                        "A style property built ${element.javaClass.name}, an additive element, which a style would " +
                            "collapse with another of its key. Declare it in the component's modifier instead."
                    }
                    val key = element.key
                    propertyElements.asMutableList().removeAll { (it as SwingModifier.NodeElement<*, *>).key == key }
                    propertyElements.add(element)
                }

                element is ParentLayoutElement && element !is ParentDataModifier && !element.additive -> {
                    val key = element.key
                    declaredLayout.asMutableList().removeAll { it.key == key }
                    declaredLayout.add(element)
                }

                else -> {
                    throw IllegalArgumentException(
                        "A style property built ${element.javaClass.name}, but a style property's element is a " +
                            "SwingModifier.NodeElement or a ParentLayoutElement that folds no parent data and is not " +
                            "additive. Declare it in the component's modifier instead.",
                    )
                }
            }
        }

        override fun release() {
            declarations.clear()
            propertyElements.clear()
            declaredLayout.clear()
            locals.clear()
            nested.clear()
            scope = null
            spare = this
        }
    }
}

/** What a collect wrote, which its caller reads until it [releases][release] it. */
internal interface StyleCollection {
    val declarations: ObjectList<PropertyElement<*, *>>

    /** The elements, by element key, in write order, the last write of a property winning. */
    val properties: ObjectList<SwingModifier.Element>

    /** The parent-layout elements, by element key, in write order. */
    val layoutElements: ObjectList<ParentLayoutElement>

    /** The values set for style locals. */
    val locals: Map<String, ProvidedLocal>

    /** The nested blocks in declaration order for each key. */
    val nested: Map<NestedStyleKey<*>, List<SwingStyle<ComponentStyleScope>>>

    /** The scope of the node's kind and state this collect ran against. */
    val scope: ComponentStyleScope?

    /** Hands this collection back to its collector for reuse; nothing may read it afterwards. */
    fun release()
}

/** A local's winning property and its element, or `null` to stop inheritance. */
internal data class ProvidedLocal(
    val property: SwingStyleProperty<*>,
    val element: PropertyElement<*, *>?,
)
