package org.jetbrains.compose.swing.modifier

/**
 * What a modifier element puts back when its declaration leaves, declared through
 * [SwingModifier.NodeElement.restores].
 *
 * A statement about what [SwingModifier.ComponentNode.onDetach] undertakes rather than an instruction to the
 * library: detaching runs the same whichever of these stands. The debug-only checks a composition can be
 * held to read it and hold a departing slot to its word.
 */
@JvmInline
public value class RestorePolicy private constructor(
    private val id: Int,
) {
    override fun toString(): String =
        when (id) {
            0 -> "EverythingWritten"
            1 -> "DeclaredPropertyOnly"
            else -> "None"
        }

    /** The three a modifier element chooses between. */
    public companion object {
        /**
         * Every property the element's write landed on stands where the modifier found it: the one the
         * element declares, and every other its setter writes with it. The default.
         */
        @JvmStatic
        public val EverythingWritten: RestorePolicy = RestorePolicy(0)

        /**
         * The property the element declares stands where the modifier found it, and what something else
         * works out from that write is left where it is.
         *
         * Declared where the write provokes a derivation the element does not make - a look and feel
         * recomputing a property of its own, a collaborator resetting its state as it is installed. Which
         * properties are derived, and from which writes, is not something the element can know: what one
         * look and feel derives is not what another does, so no list of names can be complete.
         */
        @JvmStatic
        public val DeclaredPropertyOnly: RestorePolicy = RestorePolicy(1)

        /**
         * The element undertakes nothing about the property: it installs content the next content
         * replaces, it leaves the property to a derivation that answers afresh as it goes, or it writes
         * onto a collaborator the component may have replaced since.
         *
         * Detaching still runs the element's own restore, as it does under every policy, so an element
         * declaring this is what makes that write harmless.
         */
        @JvmStatic
        public val None: RestorePolicy = RestorePolicy(2)
    }
}
