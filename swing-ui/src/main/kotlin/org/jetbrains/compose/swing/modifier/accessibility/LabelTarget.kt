@file:JvmMultifileClass
@file:JvmName("AccessibilityModifiersKt")

package org.jetbrains.compose.swing.modifier.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
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
public class LabelTarget internal constructor() {
    // The captioned component, or null while no labelTarget modifier binds this reference.
    private var target: Component? = null

    // The labels pointing at this reference; each keeps its labelFor in sync with target. A single
    // reference may caption more than one label.
    private val labels = mutableListOf<JLabel>()

    /** Binds [component] as the captioned target, displacing any previously bound one. */
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
 * the same [target] wires its `JLabel.setLabelFor` to this component. The association tracks the live
 * component and needs no name, tag, or layout pass.
 *
 * @param target the label-target reference this component is bound to.
 */
public fun SwingModifier.labelTarget(target: LabelTarget): SwingModifier = this then LabelTargetElement(target)

/**
 * Marks this label as the caption for the component bound to [target] via the [labelTarget] modifier,
 * wiring `JLabel.setLabelFor` so the label's mnemonic moves focus to that component and assistive
 * technologies read the two as a pair. Requires a `JLabel` target.
 *
 * @param target the label-target reference identifying the captioned component.
 */
public fun SwingModifier.labelFor(target: LabelTarget): SwingModifier = this then LabelForElement(target)

private class LabelTargetElement(
    private val target: LabelTarget,
) : SwingModifier.Element<Component, LabelTargetElement.Node> {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.target = target
    }

    class Node : SwingModifier.Node<Component>() {
        // Rebinds this component from the old reference to the new whenever the passed reference changes;
        // detach clears it. `component` is valid for the whole attach..detach window these run in.
        var target: LabelTarget? = null
            set(value) {
                if (value === field) return
                field?.unbindTarget(component)
                field = value
                value?.bindTarget(component)
            }

        override fun onDetach() {
            target = null
        }
    }
}

private class LabelForElement(
    private val target: LabelTarget,
) : SwingModifier.Element<JLabel, LabelForElement.Node> {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.target = target
    }

    class Node : SwingModifier.Node<JLabel>() {
        // Registers this label with the reference so its labelFor tracks whichever component is bound;
        // re-registers when the passed reference changes and deregisters on detach.
        var target: LabelTarget? = null
            set(value) {
                if (value === field) return
                field?.removeLabel(component)
                field = value
                value?.addLabel(component)
            }

        override fun onDetach() {
            target = null
        }
    }
}
