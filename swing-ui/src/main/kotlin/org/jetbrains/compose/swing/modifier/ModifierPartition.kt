package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
import org.jetbrains.compose.swing.layout.requireInheritableIsNotAdditive
import org.jetbrains.compose.swing.modifier.layout.SlotElement
import org.jetbrains.compose.swing.util.fastForEach

/**
 * One modifier taken apart as the walk over it reaches each entry: the elements the diff has nodes for,
 * split into the keyed ones and the additive ones, and the declarations the node holder reads instead.
 *
 * Built by the walk and dropped after it, so nothing of one pass outlives the pass that made it.
 *
 * @param keyedCapacity how many keyed elements the modifier is expected to declare, which [keyed] is sized for.
 * @param chainCapacity how many elements the modifier is expected to hold slots for, which [chain] is sized for.
 */
internal class ModifierPartition(
    keyedCapacity: Int = 0,
    chainCapacity: Int = 0,
) {
    /** The keyed (last-wins) elements of the modifier being applied, by [SwingModifier.NodeElement.key]. */
    val keyed: LinkedHashMap<Any, SwingModifier.NodeElement<*, *>> =
        // Holds keyedCapacity entries under the default load factor of 0.75 without growing.
        LinkedHashMap(keyedCapacity + keyedCapacity / 3 + 1)

    /**
     * The elements whose nodes hold a place in the modifier, in declaration order: every additive (subscription)
     * [SwingModifier.NodeElement], and every [ParentLayoutNodeElement] among [parentLayoutElements]; see
     * [isChainElement].
     */
    val chain: ArrayList<SwingModifier.Element> = ArrayList(chainCapacity)

    private val pendingParentDeclarations: ArrayList<ParentElement> = ArrayList()

    private var resolvedParentDeclarations: ResolvedParentDeclarations? = null

    /** The parent-layout declarations retained after their [key][ParentElement.key] resolution. */
    val parentDeclarations: List<ParentElement>
        get() = resolved().declarations

    /** The parent data folded from the retained [ParentDataModifier]s, or `null` where none is declared. */
    val parentData: Any?
        get() = resolved().parentData

    /** The family of [parentData], or `null` where no parent-data modifier is declared. */
    val parentProtocol: ParentProtocol?
        get() = resolved().parentProtocol

    /** The retained parent-layout declarations a measuring parent still interprets after parent-data folding. */
    val parentLayoutElements: List<ParentLayoutElement>
        get() = resolved().elements

    /** The one retained built-in slot declaration, or `null` when none remains. */
    val slot: SlotElement?
        get() = resolved().slot

    /**
     * The keys the modifier ties its application to, in the order they stand, or `null` where it
     * declares none - which is a key of its own: a modifier that gives its keys up is applied from
     * scratch the way one that changes them is.
     */
    val keys: List<Any?>? get() = if (keyElements.isEmpty()) null else keyElements.flatMap { it.tokens }

    /** The key declarations the walk has taken, so one declared twice is not counted twice. */
    private var keyElements: List<KeyElement> = emptyList()

    /** Routes one entry of the modifier being applied to whatever reads it. */
    fun take(element: SwingModifier.Element) {
        when (element) {
            is SwingModifier.NodeElement<*, *> -> {
                require(element !is ParentLayoutElement) {
                    "A modifier entry cannot both write a component through SwingModifier.NodeElement and " +
                        "declare layout data to its parent through ParentLayoutElement. " +
                        "Split it into one entry for each responsibility."
                }
                takeElement(element)
            }

            is ParentElement -> {
                takeParentElement(element)
            }

            is KeyElement -> {
                takeKeys(element)
            }

            else -> {
                throw IllegalArgumentException(
                    "A modifier entry is either a SwingModifier.NodeElement, which carries the node that " +
                        "writes onto the component, or one of the declarations this library reads itself. " +
                        "${element.javaClass.name} is neither, and nothing would apply it. Extend " +
                        "SwingModifier.NodeElement instead.",
                )
            }
        }
    }

    private fun takeElement(element: SwingModifier.NodeElement<*, *>) {
        if (element.isChainElement) {
            chain.add(element)
            return
        }
        // Last wins the place as well as the value: the modifier settles two writes of one property by its
        // own order, so a key declared again later stands where it was declared last. The key is asked for
        // once, which is what the walk over a modifier costs.
        val key = element.key
        keyed.remove(key)
        keyed[key] = element
    }

    /**
     * Records every parent declaration so keyed and additive declarations share one order. A node-backed layout
     * declaration also takes its place in [chain], which it gives up to a later declaration of its key, as
     * [retainedParentDeclarations] does. A layout declaration is
     * [refused where inheritable and additive][requireInheritableIsNotAdditive].
     */
    private fun takeParentElement(element: ParentElement) {
        if (element is ParentLayoutElement) element.requireInheritableIsNotAdditive()
        resolvedParentDeclarations = null
        pendingParentDeclarations += element
        if (!element.additive) {
            chain.removeAll { it is ParentLayoutNodeElement<*> && !it.additive && it.key == element.key }
        }
        if (element.isChainElement) chain += element
    }

    /** Folds the retained parent data only after key resolution, so replaced declarations cannot contribute. */
    internal fun resolved(): ResolvedParentDeclarations =
        resolvedParentDeclarations
            ?: resolveParentDeclarations(pendingParentDeclarations).also { resolvedParentDeclarations = it }

    /**
     * Declaring again adds keys, so a builder tying its own writes to a key keeps its caller's. One
     * declaration repeated adds nothing the modifier is not already tied to.
     */
    private fun takeKeys(element: KeyElement) {
        // Last wins the place as well as the value, the way a property key does: a declaration repeated
        // stands where it was declared last, and counts once.
        keyElements = keyElements.filterNot { it == element } + element
    }
}

/** This modifier's parent declarations, resolved by [resolveParentDeclarations]. Reads no other entry's key. */
internal fun SwingModifier.resolvedParentDeclarations(): ResolvedParentDeclarations =
    resolveParentDeclarations(
        foldIn(ArrayList<ParentElement>()) { declarations, element ->
            if (element is ParentElement) declarations += element
            declarations
        },
    )

/**
 * [declarations] resolved by [key][ParentElement.key]: each non-additive key keeps its last declaration, and the
 * retained parent data folds in declaration order. Refuses parent data of two layout families, and more than one
 * slot.
 */
internal fun resolveParentDeclarations(declarations: List<ParentElement>): ResolvedParentDeclarations {
    if (declarations.isEmpty()) return NoParentDeclarations
    val retained = retainedParentDeclarations(declarations)
    var parentProtocol: ParentProtocol? = null
    var parentData: Any? = null
    var slot: SlotElement? = null
    val elements = ArrayList<ParentLayoutElement>()
    retained.fastForEach { element ->
        if (element is ParentDataModifier) {
            val protocol = element.parentProtocol
            require(parentProtocol == null || protocol === parentProtocol) {
                "A child declares parent data for incompatible layout families: " +
                    "${parentProtocol?.description}, ${protocol.description}. Apply declarations " +
                    "only from the scope of its immediate parent."
            }
            parentProtocol = protocol
            parentData = element.modifyParentData(parentData)
        } else if (element is ParentLayoutElement) {
            elements.add(element)
        }
        if (element is ParentSlotElement) {
            require(slot == null) { "A modifier can retain at most one parent slot declaration." }
            require(element is SlotElement) {
                "Parent slot declarations must be created with SwingModifier.slot()."
            }
            slot = element
        }
    }
    return ResolvedParentDeclarations(retained, parentData, parentProtocol, elements, slot)
}

/** Keeps each non-additive key's final declaration without moving it across additive declarations. */
internal fun retainedParentDeclarations(declarations: List<ParentElement>): List<ParentElement> {
    if (declarations.isEmpty()) return emptyList()
    val retained = ArrayList<ParentElement>(declarations.size)
    val seenKeys = HashSet<Any>()
    for (index in declarations.lastIndex downTo 0) {
        val element = declarations[index]
        if (element.additive || seenKeys.add(element.key)) retained += element
    }
    retained.reverse()
    return retained
}

/**
 * What [resolveParentDeclarations] keeps of a list of parent declarations.
 *
 * @property declarations the retained declarations, in declaration order.
 * @property parentData the parent data the retained [ParentDataModifier]s fold to, or `null` where none is declared.
 * @property parentProtocol the family of [parentData], or `null` without a parent-data modifier.
 * @property elements the retained parent-layout declarations that fold no parent data.
 * @property slot the one retained slot declaration, or `null` when none remains.
 */
internal class ResolvedParentDeclarations(
    val declarations: List<ParentElement>,
    val parentData: Any?,
    val parentProtocol: ParentProtocol?,
    val elements: List<ParentLayoutElement>,
    val slot: SlotElement?,
)

/** What a list declaring no parent element resolves to. */
private val NoParentDeclarations =
    ResolvedParentDeclarations(
        declarations = emptyList(),
        parentData = null,
        parentProtocol = null,
        elements = emptyList(),
        slot = null,
    )

/**
 * Whether this element's node holds a place in the modifier's chain, matched by position: an additive
 * [SwingModifier.NodeElement], or a [ParentLayoutNodeElement] that folds no parent data.
 */
internal val SwingModifier.Element.isChainElement: Boolean
    get() =
        if (this is SwingModifier.NodeElement<*, *>) {
            additive
        } else {
            this is ParentLayoutNodeElement<*> && this !is ParentDataModifier
        }
