package org.jetbrains.compose.swing.modifier

import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
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
 * @param positionalCapacity how many elements the modifier is expected to hold slots for, which [positional] is
 *   sized for.
 */
internal class ModifierPartition(
    keyedCapacity: Int = 0,
    positionalCapacity: Int = 0,
) {
    /** The keyed (last-wins) elements of the modifier being applied, by [SwingModifier.NodeElement.key]. */
    val keyed: ObjectList<SwingModifier.Element> get() = keyedElements

    /** Keyed declarations mutated only while this partition takes elements. */
    private val keyedElements = MutableObjectList<SwingModifier.Element>(keyedCapacity)

    /** The [SwingModifier.NodeElement.key] of each of [keyed], asked once when the walk takes the element. */
    val keyedKeys: ObjectList<Any> get() = keyedKeyList

    /** Keys mutated only while this partition takes elements. */
    private val keyedKeyList = MutableObjectList<Any>(keyedCapacity)

    /**
     * The elements whose nodes hold a place in the modifier, in declaration order: every additive (subscription)
     * [SwingModifier.NodeElement], and every [ParentLayoutNodeElement] among [parentLayoutElements]; see
     * [isPositionalElement].
     */
    val positional: ObjectList<SwingModifier.Element> get() = positionalElements

    /** Positional declarations mutated only while this partition takes elements. */
    private val positionalElements = MutableObjectList<SwingModifier.Element>(positionalCapacity)

    private val pendingParentDeclarations: ArrayList<ParentElement> = ArrayList()

    private var resolvedParentDeclarations: ResolvedParentDeclarations? = null

    /** The parent-layout declarations retained after their [key][ParentElement.key] resolution. */
    val parentDeclarations: List<ParentElement>
        get() = resolveParentDeclarations().declarations

    /** The parent data folded from the retained [ParentDataModifier]s, or `null` where none stand. */
    val parentData: Any?
        get() = resolveParentDeclarations().parentData

    /** The family of [parentData], or `null` where no parent-data modifier stands. */
    val parentProtocol: ParentProtocol?
        get() = resolveParentDeclarations().parentProtocol

    /** The retained parent-layout declarations a measuring parent still interprets after parent-data folding. */
    val parentLayoutElements: List<ParentLayoutElement>
        get() = resolveParentDeclarations().elements

    /** The one retained built-in slot declaration, or `null` when none remains. */
    val slot: SlotElement?
        get() = resolveParentDeclarations().slot

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
        if (element.isPositionalElement) {
            positionalElements.add(element)
            return
        }
        // Last wins the place as well as the value: the modifier settles two writes of one property by its
        // own order, so a key declared again later stands where it was declared last. The key is asked for
        // once, which is what the walk over a modifier costs.
        val key = element.key
        val previous = keyedKeyList.indexOf(key)
        element.checkPreviousDeclaration(if (previous >= 0) keyedElements[previous] else null)
        if (previous >= 0) {
            keyedElements.removeAt(previous)
            keyedKeyList.removeAt(previous)
        }
        keyedElements.add(element)
        keyedKeyList.add(key)
    }

    /**
     * Records every parent declaration so keyed and additive declarations share one order. A node-backed layout
     * declaration also takes its place in [positional], which it gives up to a later declaration of its key, as
     * [retainedParentDeclarations] does. A layout declaration is
     * [refused where inheritable and additive][requireInheritableIsNotAdditive].
     */
    private fun takeParentElement(element: ParentElement) {
        if (element is ParentLayoutElement) element.requireInheritableIsNotAdditive()
        resolvedParentDeclarations = null
        pendingParentDeclarations += element
        if (!element.additive) {
            positionalElements.asMutableList().removeAll {
                it is ParentLayoutNodeElement<*> && !it.additive && it.key == element.key
            }
        }
        if (element.isPositionalElement) positionalElements.add(element)
    }

    /** Folds the retained parent data only after key resolution, so replaced declarations cannot contribute. */
    private fun resolveParentDeclarations(): ResolvedParentDeclarations =
        resolvedParentDeclarations ?: buildResolvedParentDeclarations().also { resolvedParentDeclarations = it }

    private fun buildResolvedParentDeclarations(): ResolvedParentDeclarations {
        if (pendingParentDeclarations.isEmpty()) return NoParentDeclarations
        val declarations = retainedParentDeclarations()
        val parentDataModifiers = declarations.filterIsInstance<ParentDataModifier>()
        val parentProtocol = parentDataModifiers.firstOrNull()?.parentProtocol
        require(parentDataModifiers.all { it.parentProtocol === parentProtocol }) {
            val types = parentDataModifiers.map { it.parentProtocol.description }.distinct().joinToString()
            "A child declares parent data for incompatible layout families: $types. Apply declarations " +
                "only from the scope of its immediate parent."
        }
        val parentData =
            parentDataModifiers.fold(null as Any?) { carried, modifier ->
                modifier.modifyParentData(carried)
            }
        val slots = declarations.filterIsInstance<ParentSlotElement>()
        require(slots.size <= 1) {
            "A modifier can retain at most one parent slot declaration."
        }
        val slot = slots.singleOrNull()
        require(slot == null || slot is SlotElement) {
            "Parent slot declarations must be created with SwingModifier.slot()."
        }
        return ResolvedParentDeclarations(
            declarations = declarations,
            parentData = parentData,
            parentProtocol = parentProtocol,
            elements =
                declarations.filterIsInstance<ParentLayoutElement>().filter { it !is ParentDataModifier },
            slot = slot as? SlotElement,
        )
    }

    /** Keeps each non-additive key's final declaration without moving it across additive declarations. */
    private fun retainedParentDeclarations(): List<ParentElement> {
        val retained = ArrayList<ParentElement>(pendingParentDeclarations.size)
        val seenKeys = HashSet<Any>()
        pendingParentDeclarations.asReversed().fastForEach { element ->
            if (element.additive || seenKeys.add(element.key)) retained += element
        }
        retained.reverse()
        return retained
    }

    /**
     * Declaring again adds keys, so a builder tying its own writes to a key keeps its caller's. One
     * declaration repeated adds nothing the modifier is not already tied to.
     */
    private fun takeKeys(element: KeyElement) {
        // Last wins the place as well as the value, the way a property key does: a declaration repeated
        // stands where it was declared last, and counts once.
        keyElements = keyElements.filterNot { it == element } + element
    }

    private class ResolvedParentDeclarations(
        val declarations: List<ParentElement>,
        val parentData: Any?,
        val parentProtocol: ParentProtocol?,
        val elements: List<ParentLayoutElement>,
        val slot: SlotElement?,
    )

    private companion object {
        /** What a modifier declaring no parent element resolves to. */
        val NoParentDeclarations =
            ResolvedParentDeclarations(
                declarations = emptyList(),
                parentData = null,
                parentProtocol = null,
                elements = emptyList(),
                slot = null,
            )
    }
}

/**
 * Whether this element is positional, its node matched by position: an additive [SwingModifier.NodeElement], or a
 * [ParentLayoutNodeElement] that folds no parent data.
 */
internal val SwingModifier.Element.isPositionalElement: Boolean
    get() =
        if (this is SwingModifier.NodeElement<*, *>) {
            additive
        } else {
            this is ParentLayoutNodeElement<*> && this !is ParentDataModifier
        }
