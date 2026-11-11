// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.annotations.CaretUpdatePolicy
import org.jetbrains.compose.swing.components.style.CaretElement
import org.jetbrains.compose.swing.components.style.caretBlinkRateElement
import org.jetbrains.compose.swing.components.style.caretUpdatePolicyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent

/**
 * Installs [caret] as the text component's caret - the object that holds the insertion point and the
 * selection, paints them, and answers the focus and mouse gestures that move them. Removing the
 * declaration puts back the caret the component carried before.
 *
 * Three things follow from the way Swing installs a caret:
 * - installing one places it at offset 0 with nothing selected, so hand this a caret that outlives a
 *   recomposition - one kept in a `remember`, or an `object` - and it is installed once, leaving the
 *   selection the user makes afterwards in place;
 * - a caret handed in here does not blink until it is given a rate, which [caretBlinkRate] declares: a
 *   look and feel gives its blink rate to the caret it created itself and to no other;
 * - a later look and feel leaves this caret in place, for the same reason - a look and feel replaces
 *   only a caret it created.
 *
 * Whether the selection stays painted while the component is unfocused is the caret's own answer,
 * recomputed on every focus change, so a caret of your own is where that answer is given:
 *
 * ```
 * val caret = remember {
 *     object : DefaultCaret() {
 *         override fun focusLost(event: FocusEvent) {
 *             super.focusLost(event)
 *             isSelectionVisible = true
 *         }
 *     }
 * }
 * TextField(state, modifier = SwingModifier.caret(caret).caretBlinkRate(500))
 * ```
 *
 * Where the caret may go is declared by [navigationFilter], and what it selects by
 * `DocumentState.selection`, the one owner of a component's selection; key bindings are declared by
 * `onKeyStroke`. A part of a text component this library ships no builder for - a highlighter, a drop
 * mode - is reachable through a [SwingModifier.NodeElement] of your own; see `docs/MODIFIERS.md`.
 *
 * @param caret the caret the component navigates and selects with.
 * @return this modifier with [caret] declared on it.
 * @see javax.swing.text.JTextComponent.setCaret
 */
@Deprecated("Set it in the component's style: style = { caret(caret) }")
public fun SwingModifier.caret(caret: Caret): SwingModifier = this then CaretElement(caret)

/**
 * How fast the component's caret blinks: the delay in milliseconds between the caret being shown and
 * being hidden again. `0` holds it steady.
 *
 * The rate belongs to the component, not to one caret. A caret that arrives later - one a [caret]
 * declared anywhere in the modifier chain installs - is given the declared rate as it is installed. Removing
 * the declaration puts the rate read at attach onto the caret the component carries then, and a caret
 * the modifier let go of keeps the declared rate.
 *
 * @param rate the delay in milliseconds between blinks, or `0` for a caret that does not blink.
 * @return this modifier with the blink rate declared on it.
 * @see javax.swing.text.Caret.setBlinkRate
 */
@Deprecated("Set it in the component's style: style = { caretBlinkRate(rate) }")
public fun SwingModifier.caretBlinkRate(rate: Int): SwingModifier = this then caretBlinkRateElement(rate)

/**
 * Sets what the caret does when the document is edited somewhere other than where the caret sits.
 *
 * [DefaultCaret.ALWAYS_UPDATE] carries the caret along with every edit whichever thread makes it, and
 * keeps the caret visible - the policy a log view sits at the end of, so appended lines scroll into
 * sight. [DefaultCaret.NEVER_UPDATE] leaves the caret at the offset it holds and does not scroll to keep
 * it visible, so a view stays where the reader put it while text arrives above; a removal that shortens
 * the document past that offset moves the caret to the end. [DefaultCaret.UPDATE_WHEN_ON_EDT], the
 * default, carries the caret along with edits made on the event dispatch thread and leaves it alone for
 * edits made off it.
 *
 * Requires a [JTextComponent] whose caret is a [DefaultCaret] - the caret a look and feel installs.
 * A caret that arrives and is not a [DefaultCaret] fails as it is installed, as it would on a pass.
 *
 * The policy belongs to the component, not to one caret, and removal puts it back, as [caretBlinkRate]
 * describes for the rate.
 *
 * @param policy the update constant written to the caret the component carries.
 * @return this modifier with the caret update policy declared on it.
 * @see javax.swing.text.DefaultCaret.setUpdatePolicy
 */
@Deprecated("Set it in the component's style: style = { caretUpdatePolicy(policy) }")
public fun SwingModifier.caretUpdatePolicy(
    @CaretUpdatePolicy policy: Int,
): SwingModifier = this then caretUpdatePolicyElement(policy)
