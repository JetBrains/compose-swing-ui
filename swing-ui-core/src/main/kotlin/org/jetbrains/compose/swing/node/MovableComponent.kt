// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.movableContentOf
import kotlinx.coroutines.Job
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyCompositionLocalMap
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.materialize
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import javax.swing.JComponent

private val MovableComponentKey = Key<MovableComponent>("org.jetbrains.compose.swing.movableComponent")

/**
 * Returns the movable placement of [component], which the caller built and keeps.
 *
 * Calling the result in a composition places [component] there, taking it from the container it is in.
 * Called at another place in the same pass - under a changed key, in the other branch of an if/else,
 * in a nested content composition - the component relocates without leaving the composition. A component has
 * one [MovableComponent], which every call of this function returns, so at most one call of it may be composed at
 * a time.
 *
 * When the placement ends - its call is no longer composed, its content is deactivated, or its content
 * composition is disposed - everything the placement's modifier wrote is given back and the component is
 * taken out of the container the placement put it in. The library never puts it back anywhere: the caller
 * adds it where it keeps it, as with any Swing component, from the code that ends the placement or later.
 * While its call stays composed, the composition decides where the component is: do not add it to another
 * container.
 *
 * Two calls composed at once in one Recomposer are refused, even in different content compositions. So is a
 * call composed in the pass that deactivates the content of another call. Under
 * independent roots, such as the windows of one application, a call that is composed while another root has its
 * call composed takes the component over: the other root's placement gives back what its modifier wrote and stays
 * empty; recomposing or moving that call does not take the component back.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param component the component to show; it stays the caller's.
 */
public fun movableComponentOf(component: JComponent): MovableComponent {
    checkEventDispatchThread()
    return component[MovableComponentKey] ?: MovableComponent(component).also { component[MovableComponentKey] = it }
}

/** The movable placement of one caller-owned component; see [movableComponentOf]. */
@Stable
public class MovableComponent internal constructor(
    component: JComponent,
) {
    private var active: CallerComponentNodeHolder? = null

    private val content: @Composable (SwingModifier) -> Unit =
        movableContentOf { modifier ->
            val materialized = currentComposer.materialize(modifier)
            val localMap = currentComposer.currentCompositionLocalMap
            ComposeNode<SwingNodeHolder<JComponent>, SwingApplier>(
                factory = { CallerComponentNodeHolder(component, this@MovableComponent) },
                update = {
                    val updater = SwingNodeUpdater(this)
                    updater.applyCompositionLocalMap(localMap)
                    updater.applyModifier(materialized)
                },
            )
        }

    /** Places the component here, with [modifier] applied while it is placed here. */
    @Composable
    @SwingComposable
    public operator fun invoke(modifier: SwingModifier = SwingModifier) {
        content(modifier)
    }

    /**
     * Makes [node] the one node that uses the component. The node that used it gives it up, unless its
     * composition runs in [recomposer]: two calls composed at once in one Recomposer are refused.
     */
    internal fun claim(
        node: CallerComponentNodeHolder,
        recomposer: Job?,
    ) {
        active?.let { previous ->
            check(!previous.isUsedIn(recomposer)) {
                "This ${node.component.javaClass.simpleName} is already placed through movableComponentOf " +
                    "elsewhere in this composition. Call its MovableComponent at one place at a time."
            }
            previous.finishUse()
        }
        active = node
    }

    /** Ends the claim of [node], if it still has it. */
    internal fun release(node: CallerComponentNodeHolder) {
        if (active === node) active = null
    }
}
