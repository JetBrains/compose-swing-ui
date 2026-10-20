package org.jetbrains.compose.swing.modifier

/** How a pass treats a declaration equal to the one its record holds. */
internal sealed interface WriteMode {
    /** Whether [incoming], the declaration [record] is offered, is kept by [record] without writing. */
    fun adopt(
        record: NodeRecord<*, *>,
        incoming: SwingModifier.Element,
    ): Boolean

    fun writes(
        held: Any,
        incoming: Any,
    ): Boolean

    /** An equal declaration keeps its node without writing. */
    data object AdoptEqual : WriteMode {
        override fun writes(
            held: Any,
            incoming: Any,
        ): Boolean = held != incoming

        override fun adopt(
            record: NodeRecord<*, *>,
            incoming: SwingModifier.Element,
        ): Boolean = record.adopt(incoming)
    }

    /** An equal declaration is written again, because an earlier record wrote or moved. */
    data object RewriteEqual : WriteMode {
        override fun writes(
            held: Any,
            incoming: Any,
        ): Boolean = true

        override fun adopt(
            record: NodeRecord<*, *>,
            incoming: SwingModifier.Element,
        ): Boolean = false
    }
}
