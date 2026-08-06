@file:JvmMultifileClass
@file:JvmName("AccessibilityModifierKt")

package org.jetbrains.compose.swing.modifier.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import java.awt.Component
import javax.swing.JLabel

/**
 * A reference to the component a label captions. Obtain one from [rememberLabelTarget], attach it to
 * the captioned component with the [labelTarget] modifier, and pass it to a label's [labelFor] modifier;
 * the label's `JLabel.setLabelFor` is then wired to that component so the label's mnemonic moves focus
 * to it and assistive technologies read the two as a pair.
 *
 * The reference carries the component directly - there is no name or tag to match and no tree search -
 * so the association holds no matter which of the label and its target is declared or laid out first.
 * Binding a second component displaces the first; a label whose target is unbound reads `null`.
 */
@Stable
public class LabelTarget internal constructor() {
    // The captioned component, null while no labelTarget modifier binds it.
    private var target: Component? = null

    // Labels captioning this reference; each keeps its labelFor in sync with target.
    private val labels = mutableListOf<JLabel>()

    /** Binds [component] as the captioned target. */
    internal fun bindTarget(component: Component) {
        target = component
        labels.forEach { it.labelFor = component }
    }

    /** Unbinds [component] if it is the currently bound target, leaving a target bound elsewhere intact. */
    internal fun unbindTarget(component: Component) {
        if (target !== component) return
        target = null
        labels.forEach { it.labelFor = null }
    }

    /** Registers [label] as captioning this reference and points it at the current target. */
    internal fun addLabel(label: JLabel) {
        if (label !in labels) labels += label
        label.labelFor = target
    }

    /** Deregisters [label] and clears the association it carried. */
    internal fun removeLabel(label: JLabel) {
        labels -= label
        label.labelFor = null
    }
}

/** Creates and remembers a [LabelTarget] that associates a label with the component it captions. */
@Composable
public fun rememberLabelTarget(): LabelTarget = remember { LabelTarget() }

/**
 * Marks this component as the captioned target of [target], so a label whose [labelFor] modifier carries
 * the same [target] wires its `JLabel.setLabelFor` to this component.
 *
 * @param target the label-target reference this component is bound to.
 * @see javax.swing.JLabel.setLabelFor
 */
public fun SwingModifier.labelTarget(target: LabelTarget): SwingModifier =
    binding(Component::class.java, target, LabelTarget::bindTarget, LabelTarget::unbindTarget)

/**
 * Marks this label as the caption for the component bound to [target] via the [labelTarget] modifier,
 * wiring `JLabel.setLabelFor` to that component. Requires a `JLabel` target.
 *
 * @param target the label-target reference identifying the captioned component.
 * @see javax.swing.JLabel.setLabelFor
 */
public fun SwingModifier.labelFor(target: LabelTarget): SwingModifier =
    binding(JLabel::class.java, target, LabelTarget::addLabel, LabelTarget::removeLabel)
