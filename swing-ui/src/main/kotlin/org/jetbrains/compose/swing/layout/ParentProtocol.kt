// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.layout

import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Container

/**
 * A declaration that the immediate parent interprets rather than a property of this component.
 *
 * The runtime resolves declarations with the same [key] as a last-wins slot and retains [additive]
 * declarations in order. Every retained declaration's [parentProtocol] validates the actual parent before
 * it is mutated: a declaration whose protocol refuses the parent fails, unless it is
 * [inheritable][ParentLayoutElement.inheritable] and folds no parent data.
 */
public interface ParentElement :
    SwingModifier.Element,
    SwingModifier.InspectableElement {
    /** The stable protocol identity that interprets this declaration. */
    public val parentProtocol: ParentProtocol<*>

    /** Identifies the declaration this element replaces, unless [additive] is true. */
    public val key: Any get() = javaClass

    /** Whether this declaration accumulates rather than replacing one with the same [key]. */
    public val additive: Boolean get() = false
}

/** A parent declaration interpreted by the immediate parent's layout protocol. */
public interface ParentLayoutElement : ParentElement {
    /**
     * Whether this declaration may be left out where the parent's protocol refuses it. `false` by default.
     *
     * Under a parent its protocol refuses, an inheritable declaration is left out of what the parent receives
     * instead of refused, whether a style writes it or the component's modifier declares it. A style provided to a
     * whole subtree can write it on every descendant, and only the parents that accept it receive it.
     *
     * An inheritable declaration is not [additive]: one that is both is refused.
     *
     * `inheritable` has no effect on parent data: a [ParentDataModifier] is refused under a parent
     * whose protocol refuses it, inheritable or not.
     */
    public val inheritable: Boolean get() = false
}

/**
 * Whether this declaration is left out, instead of refused, under a parent whose protocol refuses it: an
 * [inheritable][ParentLayoutElement.inheritable] one that folds no parent data.
 */
internal val ParentLayoutElement.mayBeLeftOut: Boolean
    get() = inheritable && this !is ParentDataModifier

/**
 * Refuses this declaration where it [may be left out][mayBeLeftOut] and is [additive][ParentElement.additive]: an
 * inheritable declaration is not additive. Parent data is exempt, since `inheritable` has no effect on it.
 */
internal fun ParentLayoutElement.requireInheritableIsNotAdditive() {
    require(!mayBeLeftOut || !additive) {
        "${javaClass.name} is inheritable and additive, and an inheritable element is not additive."
    }
}

/** A parent declaration that installs a child through one of the host's dedicated slots. */
@SwingComponentLibraryApi
public interface ParentSlotElement : ParentElement

/**
 * An opaque protocol identity for an immediate parent of type [H].
 *
 * Protocols are compared by identity (`===`), not [equals]. Keep one instance per parent protocol and
 * reuse it for every declaration that belongs to it.
 *
 * [H] is the host type a parent must have for this protocol to interpret a declaration.
 */
@SwingComponentLibraryApi
public interface ParentProtocol<H : Container> {
    /** The host type [accepts] requires of a parent. */
    public val hostType: Class<H>

    /** A readable name used when this protocol is declared under the wrong parent. */
    public val description: String

    /** Whether [parent] implements this protocol. */
    public fun accepts(parent: Container): Boolean
}

/**
 * Creates one stable [ParentProtocol] identity for hosts of type [H].
 *
 * @param description a readable name used when the protocol is declared under the wrong parent.
 * @param accepts whether an [H] parent implements the protocol; every [H] by default. A parent that is not an
 *   [H] is refused without asking it.
 */
public inline fun <reified H : Container> parentProtocolOf(
    description: String,
    noinline accepts: (H) -> Boolean = { true },
): ParentProtocol<H> = parentProtocolOf(H::class.java, description, accepts)

/** The [ParentProtocol] [parentProtocolOf] creates, for hosts of [hostType]. */
@PublishedApi
internal fun <H : Container> parentProtocolOf(
    hostType: Class<H>,
    description: String,
    accepts: (H) -> Boolean,
): ParentProtocol<H> {
    require(description.isNotEmpty()) { "A parent protocol needs a non-empty description." }
    return PredicateProtocol(hostType, description, accepts)
}

/** The protocol [parentProtocolOf] creates: hosts of [hostType] that satisfy [predicate]. */
private class PredicateProtocol<H : Container>(
    override val hostType: Class<H>,
    override val description: String,
    val predicate: (H) -> Boolean,
) : ParentProtocol<H> {
    override fun accepts(parent: Container): Boolean = hostType.isInstance(parent) && predicate(hostType.cast(parent))
}

/**
 * [parent] as the [H] this protocol interprets, or `null` where it refuses it: a parent that is not a [hostType], or
 * one [accepts] refuses. The class is checked and cast once.
 */
internal fun <H : Container> ParentProtocol<H>.acceptedHost(parent: Container): H? =
    if (hostType.isInstance(parent) && accepts(parent)) hostType.cast(parent) else null
