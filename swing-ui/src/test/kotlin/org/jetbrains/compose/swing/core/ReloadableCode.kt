// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import javax.swing.JPanel

/**
 * What a reload redefines: the text [Reloadable] shows and the failures it raises. Plain fields rather
 * than snapshot state, so changing one invalidates nothing, as an edit to the code invalidates nothing:
 * only [invalidate] or a reload makes the content read them again.
 */
internal class ReloadableCode {
    var text = "v1"

    /** Thrown while [Reloadable] composes. */
    var compositionFailure: Throwable? = null

    /** Thrown while the changes [Reloadable] composed are applied. */
    var applyFailure: Throwable? = null

    /** Thrown by an effect [Reloadable] launches. */
    var effectFailure: Throwable? = null

    var scope: RecomposeScope? = null

    /** Recomposes [Reloadable], as a state it read changing would. */
    fun invalidate() = checkNotNull(scope) { "Reloadable has not composed" }.invalidate()
}

/**
 * A label showing `<text>#<remembered>`, with `@<parentRemembered>` appended when it has one. The
 * remembered text is [code]'s text when it first composed, so a label whose two texts differ shows
 * remembered state that outlived an edit to the text, and one whose texts agree shows state composed
 * again from scratch.
 */
@Composable
internal fun Reloadable(
    code: ReloadableCode,
    parentRemembered: String? = null,
) {
    code.scope = currentRecomposeScope
    val remembered = remember { code.text }
    raise(code.compositionFailure)
    SideEffect { raise(code.applyFailure) }
    code.effectFailure?.let { failure -> LaunchedEffect(failure) { raise(failure) } }
    Label(text = "${code.text}#$remembered${parentRemembered?.let { "@$it" }.orEmpty()}")
}

/**
 * [Reloadable] under a parent group of its own, which remembers [code]'s text when it first composed
 * and hands it down to show. A reload that redefines only [Reloadable] keeps that value, and one that
 * composes everything again replaces it.
 */
@Composable
internal fun ReloadableInParent(code: ReloadableCode) {
    val remembered = remember { code.text }
    Reloadable(code, parentRemembered = remembered)
}

private fun raise(failure: Throwable?) {
    if (failure != null) throw failure
}

/**
 * The key of [Reloadable]'s group, which a hot reload agent invalidates after redefining [Reloadable].
 * The compiler derives it from the function, so it is read off a composition of its own on a recomposer
 * that never runs.
 */
internal fun reloadableGroupKey(): Int {
    val code = ReloadableCode()
    val recomposer = Recomposer(Dispatchers.Swing)
    var data: CompositionData? = null
    val content =
        JPanel().setContent(parent = recomposer) {
            data = currentComposer.compositionData
            Reloadable(code)
        }
    try {
        val scope = checkNotNull(code.scope)
        // A restart group keeps its recompose scope in its own slots.
        val group = checkNotNull(data).allGroups().first { group -> group.data.any { it === scope } }
        return group.key as Int
    } finally {
        content.dispose()
        recomposer.cancel()
    }
}

private fun CompositionData.allGroups(): Sequence<CompositionGroup> =
    compositionGroups.asSequence().flatMap { group -> sequenceOf(group) + group.allGroups() }

/**
 * A failure of the kind the Compose runtime raises when its own invariants break. The runtime treats it
 * as fatal in every mode, hot reload included. Its type is internal to the runtime, so it is built through
 * reflection.
 */
internal fun composeRuntimeError(message: String): Throwable = Class
    .forName("androidx.compose.runtime.ComposeRuntimeError")
    .getDeclaredConstructor(String::class.java)
    .newInstance(message) as Throwable
