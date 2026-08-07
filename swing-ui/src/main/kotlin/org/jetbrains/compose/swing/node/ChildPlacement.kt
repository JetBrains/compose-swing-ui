package org.jetbrains.compose.swing.node

/**
 * How a node holds the children composed under it, as the node itself declares it on [SwingNode].
 *
 * A Swing container reaches its children in one of two ways. It either adds them to its own index space,
 * the way `Container.add` does, and lays them out through its layout manager; or it holds each child in
 * a named region of its own, reached through a setter written for that region - a `JScrollPane`'s
 * `setViewportView` and `setCorner`, a `JTabbedPane`'s `insertTab`. The two are not interchangeable: a
 * component added by index to a host whose regions are what it lays out is held by the host and laid out
 * by nobody. So the host states which of them its children use, and every child composed under it is held
 * to that.
 *
 * A child names the region it fills on its own modifier chain, through
 * [org.jetbrains.compose.swing.modifier.layout.slot] - which a container's scope offers as a builder a
 * caller writes plainly, such as `SwingModifier.viewport()`, or declares for the caller from a composable
 * of its own, the way a desktop's `InternalFrame` puts a frame on the desktop.
 *
 * @see SwingNode
 */
public sealed interface ChildPlacement {
    /**
     * Children are added to the container by index and laid out by its layout manager: the placement of a
     * plain `JPanel`, and the one a node holds unless it declares another. A child naming a region is
     * refused here, since no host of one is in reach.
     */
    public data object Indexed : ChildPlacement

    /**
     * Every child fills one of the host's named regions, and each region holds at most one child - a
     * `JScrollPane`, whose viewport, row header, column header and corners each show a single component.
     *
     * Two declarations naming the same regions in the same order are equal, so a host that states its
     * placement afresh on every recomposition states the same one.
     *
     * @property names the calls that fill this host's regions, each written exactly as a caller of the
     *   container writes it - `"SwingModifier.viewport()"`, `"SwingModifier.corner(position)"`. They are
     *   the text an error about this host's regions prints, and a caller acts on that text by typing it,
     *   so a name that is not the call itself sends them to something they cannot write. What a child
     *   declares is resolved against the host's own setters rather than against this list.
     */
    public class Slots(
        public val names: List<String>,
    ) : ChildPlacement {
        /** The same declaration, written out: `Slots("SwingModifier.viewport()", "SwingModifier.rowHeader()")`. */
        public constructor(vararg names: String) : this(names.toList())

        override fun equals(other: Any?): Boolean = this === other || (other is Slots && names == other.names)

        override fun hashCode(): Int = names.hashCode()

        override fun toString(): String = "Slots(names=$names)"
    }

    /**
     * Every child fills a region of the host, and the host holds any number of them in the order they are
     * composed - a `JTabbedPane`, which holds one page per tab and takes a new one at the position its
     * child is composed at.
     *
     * Two declarations naming the same region are equal, so a host that states its placement afresh on
     * every recomposition states the same one.
     *
     * @property name the call that fills one of this host's regions, written exactly as a caller of the
     *   container writes it - `"SwingModifier.tab(title)"` for a tab a modifier declares,
     *   `"InternalFrame(...)"` for a region a composable of the container's own scope fills. It is the
     *   text an error about this host's regions prints, and a caller acts on that text by typing it.
     */
    public class OrderedSlots(
        public val name: String,
    ) : ChildPlacement {
        override fun equals(other: Any?): Boolean = this === other || (other is OrderedSlots && name == other.name)

        override fun hashCode(): Int = name.hashCode()

        override fun toString(): String = "OrderedSlots(name=$name)"
    }
}
