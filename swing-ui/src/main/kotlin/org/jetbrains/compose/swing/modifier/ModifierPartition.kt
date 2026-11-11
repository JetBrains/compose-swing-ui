// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
import org.jetbrains.compose.swing.layout.requireInheritableIsNotAdditive
import org.jetbrains.compose.swing.modifier.layout.SlotElement
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.style.StyleElement
import org.jetbrains.compose.swing.style.collectsNothingUnder
import org.jetbrains.compose.swing.style.requireKindTarget
import org.jetbrains.compose.swing.util.fastForEach

/**
 * One modifier taken apart as the walk over it reaches each entry: the elements the diff has nodes for,
 * split into the keyed ones and the additive ones, and the declarations the node holder reads instead.
 *
 * Built by the walk and dropped after it, so nothing of one pass outlives the pass that made it.
 *
 * @param keyedCapacity how many keyed elements the modifier is expected to declare, which [keyed] is sized for.
 * @param positionalCapacity how many elements the modifier is expected to hold records for, which [positional] is
 *   sized for.
 * @param holder the node the modifier is applied to; a style declaration that can collect nothing under its
 *   ancestors is counted without a slot. `null` takes every style declaration.
 */
internal class ModifierPartition(
    keyedCapacity: Int = 0,
    positionalCapacity: Int = 0,
    private val holder: SwingNodeHolder<*>? = null,
) {
    /** The keyed (last-wins) elements of the modifier being applied, by [SwingModifier.NodeElement.key]. */
    val keyed: List<SwingModifier.NodeElement<*, *>> get() = keyedElements

    /** Keyed declarations mutated only while this partition takes elements. */
    private val keyedElements = ArrayList<SwingModifier.NodeElement<*, *>>(keyedCapacity)

    /**
     * The elements whose nodes hold a place in the modifier, in declaration order: every additive (subscription)
     * [SwingModifier.NodeElement], and every [ParentLayoutNodeElement] among [parentLayoutElements]; see
     * [isPositionalElement].
     */
    val positional: List<SwingModifier.Element> get() = positionalElements

    /** Positional declarations mutated only while this partition takes elements. */
    private val positionalElements = ArrayList<SwingModifier.Element>(positionalCapacity)

    /** The family of each of [positional], at the same index, stated where the element entered this partition. */
    val positionalFamilies: List<ModifierNodeType<*>> get() = positionalFamilyList

    /** Families mutated only while this partition takes elements. */
    private val positionalFamilyList = ArrayList<ModifierNodeType<*>>(positionalCapacity)

    /**
     * How many of [positional] are declared in [family].
     *
     * @param family the family counted.
     */
    fun positionalCount(family: ModifierNodeType<*>): Int {
        var count = 0
        positionalFamilyList.fastForEach { if (it === family) count++ }
        return count
    }

    private val pendingParentDeclarations = ArrayList<ParentElement>()

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

    /**
     * Routes one entry of the modifier being applied to whatever reads it.
     *
     * @param element the entry to route.
     */
    fun take(element: SwingModifier.Element) {
        element.requireSingleElementRole()
        when (element) {
            is SwingModifier.NodeElement<*, *> -> {
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

    /** How many style declarations the walk has counted, taken or not; the next one's ordinal. */
    var styleCount: Int = 0
        private set

    /** Counts a style declaration that holds no slot, so the declarations after it keep their ordinals. */
    fun skipStyle() {
        styleCount++
    }

    private fun takeElement(given: SwingModifier.NodeElement<*, *>) {
        if (given is StyleElement<*, *> && holder != null && given.collectsNothingUnder(holder)) {
            requireKindTarget(given.kind, holder.component)
            skipStyle()
            return
        }
        val element = if (given is StyleElement<*, *>) given.atOrdinal(styleCount++) else given
        if (element.isPositionalElement) {
            positionalElements.add(element)
            positionalFamilyList.add(ComponentNodeType)
            return
        }
        // Last wins the place as well as the value: the modifier settles two writes of one property by its
        // own order, so a key declared again later stands where it was declared last.
        val key = element.key
        val previous = keyedElements.indexOfFirst { it.key == key }
        element.checkPreviousDeclaration(if (previous >= 0) keyedElements[previous] else null)
        if (previous >= 0) {
            keyedElements.removeAt(previous)
        }
        keyedElements.add(element)
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
        pendingParentDeclarations.add(element)
        if (!element.additive) {
            // A non-additive layout node declaration of one key is never held twice, so at most one is stale.
            val stale =
                positionalElements.indexOfLast { previous ->
                    previous is ParentLayoutNodeElement<*> && !previous.additive && previous.key == element.key
                }
            if (stale >= 0) {
                positionalElements.removeAt(stale)
                positionalFamilyList.removeAt(stale)
            }
        }
        if (element.isPositionalElement) {
            positionalElements.add(element)
            positionalFamilyList.add(ParentLayoutNodeType)
        }
    }

    /** Folds the retained parent data only after key resolution, so replaced declarations cannot contribute. */
    internal fun resolved(): ResolvedParentDeclarations {
        resolvedParentDeclarations?.let { return it }
        val resolved = resolveParentDeclarations(pendingParentDeclarations)
        resolvedParentDeclarations = resolved
        return resolved
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
}

/** This modifier's parent declarations, resolved by [resolveParentDeclarations]. Reads no other entry's key. */
internal fun SwingModifier.resolvedParentDeclarations(): ResolvedParentDeclarations =
    resolveParentDeclarations(
        foldIn(ArrayList<ParentElement>()) { declarations, element ->
            if (element is ParentElement) declarations.add(element)
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
    val parentProtocol = parentProtocol(retained)
    var parentData: Any? = null
    var slot: ParentSlotElement? = null
    var slotCount = 0
    val elements = ArrayList<ParentLayoutElement>()
    retained.fastForEach { element ->
        if (element is ParentDataModifier) parentData = element.modifyParentData(parentData)
        if (element is ParentSlotElement) {
            slot = element
            slotCount++
        }
        if (element is ParentLayoutElement && element !is ParentDataModifier) elements.add(element)
    }
    require(slotCount <= 1) {
        "A modifier can retain at most one parent slot declaration."
    }
    require(slot == null || slot is SlotElement) {
        "Parent slot declarations must be created with SwingModifier.slot()."
    }
    return ResolvedParentDeclarations(
        declarations = retained,
        parentData = parentData,
        parentProtocol = parentProtocol,
        elements = elements,
        slot = slot as? SlotElement,
    )
}

/** Validates the entire parent-data family before any declaration folds its data. */
private fun parentProtocol(declarations: List<ParentElement>): ParentProtocol? {
    var parentProtocol: ParentProtocol? = null
    var compatible = true
    declarations.fastForEach { element ->
        if (compatible && element is ParentDataModifier) {
            if (parentProtocol == null) parentProtocol = element.parentProtocol
            if (element.parentProtocol !== parentProtocol) compatible = false
        }
    }
    require(compatible) {
        val types = ArrayList<String>()
        declarations.fastForEach { element ->
            if (element is ParentDataModifier) {
                val description = element.parentProtocol.description
                if (description !in types) types.add(description)
            }
        }
        "A child declares parent data for incompatible layout families: ${types.joinToString()}. " +
            "Apply declarations only from the scope of its immediate parent."
    }
    return parentProtocol
}

/** Keeps each non-additive key's final declaration without moving it across additive declarations. */
internal fun retainedParentDeclarations(declarations: List<ParentElement>): List<ParentElement> {
    if (declarations.isEmpty()) return emptyList()
    val retained = ArrayList<ParentElement>(declarations.size)
    val seenKeys = HashSet<Any>()
    declarations.asReversed().fastForEach { element ->
        if (element.additive || seenKeys.add(element.key)) retained.add(element)
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

/** Rejects an element that declares both component writes and parent layout data. */
internal fun SwingModifier.Element.requireSingleElementRole() {
    require(this !is SwingModifier.NodeElement<*, *> || this !is ParentLayoutElement) {
        "A modifier entry cannot both write a component through SwingModifier.NodeElement and " +
            "declare layout data to its parent through ParentLayoutElement. " +
            "Split it into one entry for each responsibility."
    }
}
