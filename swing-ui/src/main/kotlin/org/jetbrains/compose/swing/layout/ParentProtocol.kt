package org.jetbrains.compose.swing.layout

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
    public val parentProtocol: ParentProtocol

    /** Identifies the declaration this element replaces, unless [additive] is true. */
    public val key: Any get() = javaClass

    /** Whether this declaration accumulates rather than replacing one with the same [key]. */
    public val additive: Boolean get() = false
}

/** A parent declaration interpreted by the immediate parent's layout protocol. */
public interface ParentLayoutElement : ParentElement {
    /**
     * Whether this declaration may be a component default. `false` by default.
     *
     * An inheritable declaration is also left out, instead of refused, under a parent whose protocol refuses it,
     * whether a component default provides it or a component declares it directly.
     *
     * An inheritable declaration is not [additive]: one that is both is refused.
     *
     * `inheritable` has no effect on parent data: whether or not it is inheritable, a [ParentDataModifier] cannot be
     * a component default, folds, and fails under a parent its protocol refuses.
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
public interface ParentSlotElement : ParentElement

/**
 * An opaque protocol identity for an immediate parent.
 *
 * Protocols are compared by identity (`===`), not [equals]. Keep one instance per parent protocol and
 * reuse it for every declaration that belongs to it.
 */
public interface ParentProtocol {
    /** A readable name used when this protocol is declared under the wrong parent. */
    public val description: String

    /** Whether [parent] implements this protocol. */
    public fun accepts(parent: Container): Boolean
}

/** Creates one stable [ParentProtocol] identity. */
public fun parentProtocolOf(
    description: String,
    accepts: (Container) -> Boolean,
): ParentProtocol {
    require(description.isNotEmpty()) { "A parent protocol needs a non-empty description." }
    return object : ParentProtocol {
        override val description: String = description

        override fun accepts(parent: Container): Boolean = accepts(parent)
    }
}
