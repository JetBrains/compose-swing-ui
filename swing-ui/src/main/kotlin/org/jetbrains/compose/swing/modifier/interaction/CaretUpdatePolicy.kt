@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.constants.CaretUpdatePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent

/**
 * Sets what the caret does when the document is edited somewhere other than where the caret sits.
 *
 * [DefaultCaret.ALWAYS_UPDATE] carries the caret along with every edit whichever thread makes it - the
 * policy a log view sits at the end of, so appended lines scroll into sight. [DefaultCaret.NEVER_UPDATE]
 * leaves the caret at the offset it holds, so a view stays where the reader put it while text arrives
 * above. [DefaultCaret.UPDATE_WHEN_ON_EDT], a caret's own policy, carries the caret along with edits
 * made on the event dispatch thread and leaves it alone for edits made off it.
 *
 * Requires a [JTextComponent] whose caret is a [DefaultCaret] - the caret a look and feel installs.
 * Removing the declaration puts back the policy the caret carried before.
 *
 * @see javax.swing.text.DefaultCaret.setUpdatePolicy
 */
public fun SwingModifier.caretUpdatePolicy(
    @CaretUpdatePolicy policy: Int,
): SwingModifier =
    this then
        propertyElement<JTextComponent, Int>(
            policy,
            read = { it.defaultCaret().updatePolicy },
            write = { c, v -> c.defaultCaret().updatePolicy = v },
        )

/** The component's caret as the [DefaultCaret] the policy is written to. */
private fun JTextComponent.defaultCaret(): DefaultCaret {
    val caret = caret
    check(caret is DefaultCaret) {
        "caretUpdatePolicy requires a ${DefaultCaret::class.java.name} caret, " +
            "but the ${javaClass.name} carries ${caret?.javaClass?.name}"
    }
    return caret
}
