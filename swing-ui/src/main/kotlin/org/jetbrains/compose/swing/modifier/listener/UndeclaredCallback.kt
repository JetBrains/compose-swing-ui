package org.jetbrains.compose.swing.modifier.listener

/*
 * A builder taking one callback per method of a listener interface defaults every one of them, so a
 * caller names only the methods they want. That leaves one call the library cannot honor: the one
 * naming none. The listener it registers is built from the callbacks the call names, so it reports
 * nowhere and no later declaration reaches it.
 *
 * The defaults below are what a builder compares against, to tell a method left undeclared from one a
 * caller declared a do-nothing lambda for.
 */

/** The default of an undeclared callback that reports an event. */
internal val UNDECLARED: (Any?) -> Unit = {}

/** The default of an undeclared callback that takes no event. */
internal val UNDECLARED_ACTION: () -> Unit = {}

/** The default of an undeclared callback that answers whether a change may happen; it allows it. */
internal val UNDECLARED_ANSWER: (Any?) -> Boolean = { true }

/** Whether [callback] is a callback the caller declared rather than one of the defaults above. */
internal fun declared(callback: Any): Boolean =
    callback !== UNDECLARED && callback !== UNDECLARED_ACTION && callback !== UNDECLARED_ANSWER

/**
 * Refuses a call that declares no callback at all.
 *
 * @param builder the name the refusal reports, as a caller wrote it.
 * @param anyDeclared whether at least one of the call's callbacks is the caller's own.
 */
internal fun requireAnyDeclared(
    builder: String,
    anyDeclared: Boolean,
) {
    require(anyDeclared) {
        "$builder declares no callback, so the listener it would register can never report anything; " +
            "declare at least one, or leave the modifier off"
    }
}
