package org.jetbrains.compose.swing.modifier.listener

import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/*
 * Several widgets publish their events through a model they hold under a bound property, and that model
 * can be replaced: a text component's document, a color chooser's and a table's selection model, a
 * table's column model, a slider's range model. A listener added to the model alone is left behind on
 * the outgoing one, where it reports changes nothing renders. A detach that resolves the property
 * afresh then removes it from the incoming model instead, so the registration outlives its own detach.
 * The pairing here keeps a registration on whichever model the component holds now, which is what makes
 * a listener declared on such a component behave like every other listener modifier.
 *
 * Moving the registration is only half of it. A listener that keeps state describing the model it sits
 * on - the value, the selection or the layout it and the caller last agreed on - would go on comparing
 * against a model that is no longer there, and a later change that happened to match that stale state
 * would read as no change at all. Such a listener declares [ModelSwapAware] and is told about the
 * replacement, which *settles* its state and reports nothing: a swap is the caller's own doing, so it
 * reaches no callback, exactly as declaring a new model on a component does.
 */

/**
 * The model a component of type [C] publishes through, and the pair that registers a listener of type
 * [L] on it.
 *
 * Declare one per registration site and hold it in a `val`.
 *
 * @param property the bound property the component publishes the model under.
 * @param modelType the model type, used to read the models a swap reports.
 * @param model the model the component holds at the moment.
 * @param add registers a listener on a model.
 * @param remove unregisters a listener from a model.
 */
internal class SwappableModel<C : Component, M : Any, L : Any>(
    private val property: String,
    private val modelType: Class<M>,
    private val model: (C) -> M,
    private val add: (M, L) -> Unit,
    private val remove: (M, L) -> Unit,
) {
    /**
     * Registers [listener] on the model [component] holds now, and moves it to the replacement whenever
     * that model is swapped out, until [detach] takes it off again.
     */
    fun attach(
        component: C,
        listener: L,
    ) {
        val current = model(component)
        add(current, listener)
        (listener as? ModelSwapAware)?.adoptModelSwap(current)
        component.addPropertyChangeListener(property, ModelSwapListener(this, listener, modelType, add, remove))
    }

    /**
     * Undoes [attach]: unregisters [listener] from the model [component] holds at this moment - the one
     * the registration followed to - and stops following further swaps.
     */
    fun detach(
        component: C,
        listener: L,
    ) {
        remove(model(component), listener)
        component
            .getPropertyChangeListeners(property)
            .filterIsInstance<ModelSwapListener<*, *>>()
            .firstOrNull { it.owner === this && it.listener === listener }
            ?.let { component.removePropertyChangeListener(property, it) }
    }
}

/**
 * Re-homes [listener] across a swap of the model: removed from the outgoing one and added to the
 * incoming one, so one registration follows the component rather than staying behind on a model nothing
 * reads.
 *
 * Held by the component rather than by the modifier chain, so the pairing survives every recomposition
 * that rebuilds the chain. [owner] together with [listener] is what tells the instance a detach has to
 * remove apart from the ones other registrations on the same property installed.
 */
private class ModelSwapListener<M : Any, L : Any>(
    val owner: SwappableModel<*, M, L>,
    val listener: L,
    private val modelType: Class<M>,
    private val add: (M, L) -> Unit,
    private val remove: (M, L) -> Unit,
) : PropertyChangeListener {
    override fun propertyChange(event: PropertyChangeEvent) {
        asModel(event.oldValue)?.let { remove(it, listener) }
        asModel(event.newValue)?.let {
            add(it, listener)
            (listener as? ModelSwapAware)?.adoptModelSwap(it)
        }
    }

    private fun asModel(value: Any?): M? = if (modelType.isInstance(value)) modelType.cast(value) else null
}

/**
 * A listener whose own state describes the model it is registered on, and which therefore has to be told
 * when that model is replaced under it.
 *
 * Implement it alongside the listener interface itself.
 */
internal fun interface ModelSwapAware {
    /**
     * Settles this listener's state against [model], the model the registration now sits on. A swap is
     * the caller's own doing, so this reports nothing: it records, it does not deliver.
     */
    fun adoptModelSwap(model: Any)
}
