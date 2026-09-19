package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.core.SwingContentComposition
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.core.disposingOnFailure
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.checkRootShowsOneChild
import java.awt.Component
import java.awt.Container

/**
 * A composition of Swing [content] under [parent]. It recomposes when parent snapshot state it reads
 * changes. [recompose] drives its own synchronous pass on the caller's thread before returning, without
 * waiting for the enclosing window's frame loop.
 *
 * [content]'s one top-level component is handed to the caller by [recompose] rather than added to a
 * container - this composition places nothing of its own. Content that composes more than one
 * top-level component is refused with [IllegalStateException], since there would be no
 * single component to hand back; composing it for the first time on construction is held to the same
 * rule. Any later refusal disposes the composition before it is reported.
 *
 * [content] shares [parent]'s state and
 * [CompositionLocal][androidx.compose.runtime.CompositionLocal]s.
 *
 * Must be constructed, recomposed, and disposed on the Event Dispatch Thread.
 *
 * @param parent the composition context this composition joins.
 * @param content content composed on construction and by [recompose].
 */
public class OnDemandComposition(
    parent: CompositionContext,
    content:
        @Composable @SwingComposable
        () -> Unit,
) : DisposableHandle {
    // Holds the top-level component currently composed.
    private var current: Component? = null
    private val rootHolder = SwingNodeHolder(Container())

    // Attaches the top-level component without placing it.
    private val slot =
        SlotAttachment { _, component, _ ->
            current = component
            return@SlotAttachment {
                // Wherever the caller placed the component they were handed, take it back out of it: the
                // slot leaving the composition must not leave a stale child behind in a container this
                // composition knows nothing about.
                component.parent?.remove(component)
                if (current === component) current = null
            }
        }

    // Prevents recursive recomposition during applier flush.
    private var recomposing = false

    private val composition: SwingContentComposition

    init {
        checkEventDispatchThread()
        val built =
            SwingContentComposition.nested(parent) { owner ->
                SwingApplier(
                    rootHolder.attachedTo(owner),
                    rootSlot = slot,
                    onRootSlotSettled = { requireOneTopLevelComponent() },
                )
            }
        composition = built
        disposingOnFailure(composition::dispose) {
            composition.setContent(content)
            requireOneTopLevelComponent()
        }
    }

    /**
     * Runs [update], then recomposes and applies this composition synchronously, so its Swing subtree is
     * fully materialized before this returns. The write is recorded against this composition so the
     * synchronous recompose sees the change; this takes no frame from a window recomposer.
     *
     * A call from inside another [recompose] runs neither [update] nor a recompose, and answers the
     * component the outer call is already in the middle of producing - the same component this call
     * would otherwise materialize again. Once this composition is [dispose]d, [update] does not run and
     * this answers `null`, the way an already-disposed [DisposableHandle] does nothing rather than
     * failing: a Swing widget keeps invoking a renderer it captured even while its window is torn down.
     *
     * A pass that composes more than one top-level component disposes this composition before throwing
     * [IllegalStateException], so later calls follow the disposed-composition behavior.
     *
     * Must be called on the Event Dispatch Thread.
     *
     * @param update writes whatever state [content] reads before this recomposes.
     * @return the one top-level component [content] composes, or `null` where it composes none.
     */
    public fun recompose(update: () -> Unit): Component? {
        checkEventDispatchThread()
        if (recomposing) return current
        recomposing = true
        try {
            composition.recomposeSynchronously(update)
            requireOneTopLevelComponent()
            return current
        } finally {
            recomposing = false
        }
    }

    /**
     * Disposes this composition. A [recompose] over it stays safe to invoke afterwards, answering `null`
     * the way a composition holding nothing composes none.
     *
     * Must be called on the Event Dispatch Thread.
     */
    override fun dispose() {
        checkEventDispatchThread()
        composition.dispose()
    }

    /**
     * Holds this composition to the one top-level component its content is allowed to compose after its
     * synchronous application has settled. A plain `index` naming where the applier installed a component
     * says nothing here, since every one of them fills the same single-occupancy root slot.
     *
     * Reuses [checkRootShowsOneChild]'s account of live root occupants; when it finds more than one, it
     * clears [current], disposes this composition, and reports the refusal as an [IllegalStateException].
     */
    private fun requireOneTopLevelComponent() {
        try {
            rootHolder.checkRootShowsOneChild()
        } catch (overflow: IllegalStateException) {
            current = null
            composition.dispose()
            throw MultipleTopLevelComponentsException(overflow.message.orEmpty(), overflow)
        }
    }
}

/**
 * An internal subtype of [IllegalStateException] used by [OnDemandComposition] when its content composes
 * more than one top-level component.
 *
 * @param message describes the refusal, naming the components content composed.
 * @param cause the failure [OnDemandComposition] itself raised the refusal from.
 */
public class MultipleTopLevelComponentsException(
    message: String,
    cause: Throwable,
) : IllegalStateException(message, cause)
