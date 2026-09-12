package org.jetbrains.compose.swing.layout

/**
 * How a node holds the children composed under it.
 *
 * A host either adds children to its own index space, or holds each child in a named region through a
 * dedicated setter. The two are not interchangeable, so every child composed under a host is held to the
 * placement the host declares.
 */
public sealed interface ChildPlacement {
    /** Children are added by index and laid out by the host's layout manager. */
    public data object Indexed : ChildPlacement

    /** Every child fills one named region, and each region holds at most one child. */
    public class Slots(
        /** The calls that fill the host's regions, as a caller writes them. */
        public val names: List<String>,
    ) : ChildPlacement {
        /** The same declaration written as `Slots("SwingModifier.viewport()")`. */
        public constructor(vararg names: String) : this(names.toList())

        override fun equals(other: Any?): Boolean = this === other || (other is Slots && names == other.names)

        override fun hashCode(): Int = names.hashCode()

        override fun toString(): String = "Slots(names=$names)"
    }

    /** Every child fills one region and the host holds any number of them in composition order. */
    public class OrderedSlots(
        /** The call that fills one of this host's regions. */
        public val name: String,
    ) : ChildPlacement {
        override fun equals(other: Any?): Boolean = this === other || (other is OrderedSlots && name == other.name)

        override fun hashCode(): Int = name.hashCode()

        override fun toString(): String = "OrderedSlots(name=$name)"
    }
}
