package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.AppliedWrite

/*
 * The two writes a selection component makes to its widget that the widget answers by dropping selection,
 * and the ownership rule both follow.
 *
 * A declared selection is the composition's state, re-asserted on every pass: what such a write leaves of it
 * is the composition's own doing, and reporting it would hand the caller's state holder a selection the user
 * never chose, for the next pass to apply in place of the declaration. An undeclared selection is the user's
 * alone and the part of it the write drops is gone for good, so the loss reaches the caller exactly once -
 * carried by the widget's own event where the write can run outside the wrapper's own writes, and handed
 * over by the wrapper where the write has to run inside them.
 */

/**
 * Runs [block] - a property write its widget answers by dropping selection the property no longer lets it
 * hold - as the wrapper's own write where [declaredSelection] is the composition's, and as a plain write
 * where the selection is the user's own.
 */
internal fun AppliedWrite.writeNarrowing(
    declaredSelection: List<*>?,
    block: () -> Unit,
) {
    if (declaredSelection == null) block() else write(block)
}

/**
 * Gives a widget new content through [install] and leaves it holding the selection that should stand:
 * [declared] where the caller declares one, and otherwise the selection the widget held before, read through
 * [selection] and put back through [apply]. New content drops a widget's selection, and the selection is not
 * the library's to destroy.
 *
 * Content the user's selection reaches past is the one case where part of that selection is gone for good.
 * Putting the selection back has to follow the install that drops it, so it runs as this wrapper's own
 * write, and the part of the selection the new content could not hold is handed to [report] afterwards.
 * Reporting it rather than leaving it to the widget's own event is what makes the loss reach the caller on
 * the pass that reactivates a parked node, where the listeners a modifier installs are detached.
 */
internal fun <S> AppliedWrite.installNarrowing(
    declared: List<S>?,
    selection: () -> List<S>,
    apply: (List<S>) -> Unit,
    report: (List<S>) -> Unit,
    install: () -> Unit,
) {
    val retained = declared ?: selection()
    write {
        install()
        apply(retained)
    }
    if (declared != null) return
    val settled = selection()
    val lost = retained.filterNot { it in settled }
    if (lost.isNotEmpty()) report(lost)
}
