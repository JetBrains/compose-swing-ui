// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.layout

import java.awt.Component
import java.awt.Container

/**
 * How a node holds the children composed under it.
 *
 * A host either adds children to its own index space, or holds each child in a named region through a
 * dedicated setter. The two are not interchangeable, so every child composed under a host is held to the
 * placement the host declares.
 *
 * A placement is typed by the host it can serve: a node whose component is a [T] accepts a `ChildPlacement<T>`,
 * so content that only a `JLayer` can install cannot be declared on any other component.
 */
public sealed class ChildPlacement<in T : Component> {
    /** Whether the host installs its unnamed children as its content. */
    internal open val hasContent: Boolean get() = false

    /** Installs [child] as the content of [host], returning the action that takes it out again. */
    internal open fun installContent(
        host: T,
        child: Component,
        index: Int,
    ): () -> Unit = error("This placement declares no content for $host to install $child at $index.")

    /** Children are added by index and laid out by the host's layout manager. */
    public data object Indexed : ChildPlacement<Component>()

    /**
     * Named regions that each hold at most one child, plus at most one unnamed child that fills the host's
     * content when it declares one.
     *
     * Every other child names the region it fills. A declaration names at least one region or declares
     * content, which [withContent] does.
     */
    public class Slots<in H : Container> private constructor(
        /** The calls that fill the host's regions, as a caller writes them. */
        public val names: List<String>,
        private val content: SlotAttachment<H>?,
    ) : ChildPlacement<H>() {
        init {
            require(names.isNotEmpty() || content != null) {
                "ChildPlacement.Slots needs a region name or a content attachment: " +
                    "a host declared with neither can hold no child."
            }
        }

        override val hasContent: Boolean get() = content != null

        override fun installContent(
            host: H,
            child: Component,
            index: Int,
        ): () -> Unit = requireNotNull(content).install(host, child, index)

        override fun equals(other: Any?): Boolean =
            this === other || (other is Slots<*> && names == other.names && content === other.content)

        override fun hashCode(): Int = 31 * names.hashCode() + System.identityHashCode(content)

        override fun toString(): String = "Slots(names=$names)"

        /** Declares a [Slots] with content typed by the host that installs it. */
        public companion object {
            /** The same declaration written as `Slots("Viewport { }")`, with no content, for any [Container]. */
            public operator fun invoke(vararg names: String): Slots<Container> = Slots(names.toList(), null)

            /**
             * Regions named [names] plus content that [content] installs into a host of type [H].
             *
             * The result serves a host of type [H] only. Declare [content] once, as a `val`, since it is compared by
             * identity.
             */
            public fun <H : Container> withContent(
                vararg names: String,
                content: SlotAttachment<H>,
            ): Slots<H> = Slots(names.toList(), content)
        }
    }

    /** Every child fills one region and the host holds any number of them in composition order. */
    public class OrderedSlots(
        /** The call that fills one of this host's regions. */
        public val name: String,
    ) : ChildPlacement<Container>() {
        override fun equals(other: Any?): Boolean = this === other || (other is OrderedSlots && name == other.name)

        override fun hashCode(): Int = name.hashCode()

        override fun toString(): String = "OrderedSlots(name=$name)"
    }
}
