package org.jetbrains.compose.swing.modifier

/** How a pass treats a declaration equal to the one its record holds. */
internal sealed interface WriteMode {
    /**
     * Whether [incoming], the declaration [record] is offered, is kept by [record] without writing.
     *
     * @param record the record offered [incoming].
     * @param incoming the declaration offered to [record].
     */
    fun adopt(
        record: NodeRecord<*, *>,
        incoming: SwingModifier.Element,
    ): Boolean

    /** Whether a declaration equal to the held one is written again. */
    val rewritesEqual: Boolean

    /**
     * Whether [incoming] is written over [held].
     *
     * @param held the declaration a record holds.
     * @param incoming the declaration offered in its place.
     */
    fun writes(
        held: SwingModifier.Element,
        incoming: SwingModifier.Element,
    ): Boolean

    /** An equal declaration keeps its node without writing. */
    data object AdoptEqual : WriteMode {
        override val rewritesEqual: Boolean get() = false

        override fun writes(
            held: SwingModifier.Element,
            incoming: SwingModifier.Element,
        ): Boolean = held != incoming

        override fun adopt(
            record: NodeRecord<*, *>,
            incoming: SwingModifier.Element,
        ): Boolean = record.adopt(incoming)
    }

    /** An equal declaration is written again, because an earlier record wrote or moved. */
    data object RewriteEqual : WriteMode {
        override val rewritesEqual: Boolean get() = true

        override fun writes(
            held: SwingModifier.Element,
            incoming: SwingModifier.Element,
        ): Boolean = true

        override fun adopt(
            record: NodeRecord<*, *>,
            incoming: SwingModifier.Element,
        ): Boolean = false
    }
}
