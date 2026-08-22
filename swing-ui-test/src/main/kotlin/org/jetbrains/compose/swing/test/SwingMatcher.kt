package org.jetbrains.compose.swing.test

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.appearance.testTagOrNull
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Frame
import javax.accessibility.AccessibleRole
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JInternalFrame
import javax.swing.text.JTextComponent

/**
 * A predicate over a single AWT [Component] together with a human-readable [description] used in
 * failure messages. Matchers are combined with [and], [or] and [not], and narrow a query wherever a
 * finder, a filter or an assertion takes one.
 *
 * Matching reads component state directly; callers are responsible for invoking matchers on the
 * EDT (the finder infrastructure does this).
 *
 * @property description what this matcher looks for, as it reads in a failure message.
 */
public class SwingMatcher internal constructor(
    public val description: String,
    private val predicate: (Component) -> Boolean,
) {
    /**
     * Evaluates this matcher against [component]. Must be called on the EDT.
     *
     * @param component the component to test; a structural matcher reaches its parent, children or
     *   ancestors through it.
     * @return `true` when [component] satisfies this matcher.
     */
    public fun matches(component: Component): Boolean = predicate(component)

    /**
     * Returns a matcher satisfied only when both this and [other] match.
     *
     * @param other evaluated only after this matcher matches.
     * @return a matcher whose description joins both, so a failure names the whole condition.
     */
    public infix fun and(other: SwingMatcher): SwingMatcher =
        SwingMatcher("($description && ${other.description})") {
            predicate(it) && other.matches(it)
        }

    /**
     * Returns a matcher satisfied when this or [other] matches.
     *
     * @param other evaluated only when this matcher does not match.
     * @return a matcher whose description joins both, as [and]'s does.
     */
    public infix fun or(other: SwingMatcher): SwingMatcher =
        SwingMatcher("($description || ${other.description})") {
            predicate(it) || other.matches(it)
        }

    /** Returns a matcher satisfied exactly when this one is not. */
    public operator fun not(): SwingMatcher = SwingMatcher("!($description)") { !predicate(it) }

    /** Built-in matchers, and the entry point for building others with [and], [or] and [not]. */
    public companion object {
        /**
         * Matches a component whose textual content equals [text], or contains it when [substring]
         * is `true`. Text is read from [javax.swing.JLabel.getText], [AbstractButton.getText], or
         * [JTextComponent.getText] depending on the component type. A password field carries no
         * readable text and never matches.
         *
         * @param text matched against the component's whole text; a component that carries none
         *   never matches.
         * @param substring `true` matches text that merely contains [text]; `false` by default.
         */
        public fun hasText(
            text: @Nls String,
            substring: Boolean = false,
        ): SwingMatcher {
            val desc = if (substring) "hasText(substring=\"$text\")" else "hasText(\"$text\")"
            return SwingMatcher(desc) { component ->
                val actual = component.textOrNull() ?: return@SwingMatcher false
                if (substring) actual.contains(text) else actual == text
            }
        }

        /**
         * Matches a component whose [Component.getName] equals [name].
         *
         * @param name the name to match; a [javax.swing.JComponent] is unnamed until something sets
         *   one and never matches, while the classic AWT widgets and every window construct a default
         *   name of their own, such as `frame0`.
         * @return a matcher whose description names this condition.
         */
        public fun hasName(name: String): SwingMatcher = SwingMatcher("hasName(\"$name\")") { it.name == name }

        /**
         * Matches a component tagged with [tag] via `SwingModifier.testTag`.
         *
         * @param tag the tag declared on the component; one carrying a different tag, or none,
         *   never matches.
         * @return a matcher whose description names this condition.
         */
        public fun hasTestTag(tag: String): SwingMatcher =
            SwingMatcher("hasTestTag(\"$tag\")") { component -> component.testTagOrNull() == tag }

        /**
         * Matches a component whose accessible name equals [name], read from its
         * [java.awt.Component.getAccessibleContext].
         *
         * @param name the name assistive technology reports: the one declared on the component, a
         *   button's or label's own text, or a fallback such as a titled border or the label
         *   labeling the component.
         * @return a matcher whose description names this condition.
         */
        public fun hasAccessibleName(name: @Nls String): SwingMatcher =
            SwingMatcher("hasAccessibleName(\"$name\")") { component ->
                component.accessibleContext?.accessibleName == name
            }

        /**
         * Matches a component whose accessible description equals [description], read from its
         * [java.awt.Component.getAccessibleContext].
         *
         * @param description the longer text assistive technology reports beside the name, which a
         *   component carries where one is declared and otherwise takes from its tooltip text.
         * @return a matcher whose description names this condition.
         */
        public fun hasAccessibleDescription(description: @Nls String): SwingMatcher =
            SwingMatcher("hasAccessibleDescription(\"$description\")") { component ->
                component.accessibleContext?.accessibleDescription == description
            }

        /**
         * Matches a component whose accessible role equals [role], read from its
         * [java.awt.Component.getAccessibleContext].
         *
         * @param role the role the component's type reports, such as [AccessibleRole.PUSH_BUTTON]
         *   for a button; nothing a test declares changes it.
         * @return a matcher whose description names this condition.
         */
        public fun hasAccessibleRole(role: AccessibleRole): SwingMatcher =
            SwingMatcher("hasAccessibleRole($role)") { component ->
                component.accessibleContext?.accessibleRole == role
            }

        /**
         * Matches a component whose title equals [title], read from [Frame.getTitle],
         * [Dialog.getTitle] or [JInternalFrame.getTitle]. Use it with [ComposeSwingTest.onWindow] to
         * pick one window out of several, and with a node query to assert the title of a frame
         * standing on a desktop.
         *
         * @param title the exact title; a component that carries no title of its own never matches.
         *   A [Frame] built without one carries the empty string, so `hasTitle("")` picks it out,
         *   while a [Dialog]'s title may be `null`.
         * @return a matcher whose description names this condition.
         */
        public fun hasTitle(title: @Nls String): SwingMatcher =
            SwingMatcher("hasTitle(\"$title\")") { component ->
                when (component) {
                    is Frame -> component.title == title
                    is Dialog -> component.title == title
                    is JInternalFrame -> component.title == title
                    else -> false
                }
            }

        /**
         * Matches a component whose enabled state equals [enabled]. A disabled component takes no user
         * input and raises no events; components start out enabled.
         *
         * @param enabled the state to require, `true` by default. A component reports its own flag,
         *   so one inside a disabled container still matches `isEnabled()`.
         */
        public fun isEnabled(enabled: Boolean = true): SwingMatcher =
            SwingMatcher("isEnabled($enabled)") { it.isEnabled == enabled }

        /**
         * Matches a component whose selected state equals [selected], read from
         * [AbstractButton.isSelected] - the state a check box, radio button, toggle button or
         * checkable menu item carries. A component that carries no selected state never matches, in
         * either direction, so `isSelected(false)` asserts "carries a selection and is off" while
         * `!isSelected()` also admits a component that cannot be selected at all.
         *
         * @param selected the state to require, `true` by default.
         */
        public fun isSelected(selected: Boolean = true): SwingMatcher =
            SwingMatcher("isSelected($selected)") { component ->
                component is AbstractButton && component.isSelected == selected
            }

        /**
         * Matches a component whose editable state equals [editable], read from
         * [JTextComponent.isEditable] for a text component and [JComboBox.isEditable] for a combo
         * box. A component that carries no editable state never matches, in either direction; see
         * [isSelected] for what that means for the negated form.
         *
         * @param editable the state to require, `true` by default.
         */
        public fun isEditable(editable: Boolean = true): SwingMatcher =
            SwingMatcher("isEditable($editable)") { component ->
                when (component) {
                    is JTextComponent -> component.isEditable == editable
                    is JComboBox<*> -> component.isEditable == editable
                    else -> false
                }
            }

        /** Matches a component that is an instance of [T]. */
        public inline fun <reified T : Component> isOfType(): SwingMatcher = ofType(T::class.java)

        /**
         * Matches a component whose parent satisfies [matcher]. A component with no parent never
         * matches.
         *
         * @param matcher applied to the direct parent only; [hasAnyAncestor] reaches further up.
         * @return a matcher whose description names this condition.
         */
        public fun hasParent(matcher: SwingMatcher): SwingMatcher =
            SwingMatcher("hasParent(${matcher.description})") { component ->
                component.parent?.let(matcher::matches) == true
            }

        /**
         * Matches a component with at least one direct child satisfying [matcher].
         *
         * @param matcher applied to each direct child in turn; [hasAnyDescendant] reaches deeper.
         * @return a matcher whose description names this condition.
         */
        public fun hasAnyChild(matcher: SwingMatcher): SwingMatcher =
            SwingMatcher("hasAnyChild(${matcher.description})") { component ->
                component.childComponents().any(matcher::matches)
            }

        /**
         * Matches a component with at least one sibling satisfying [matcher]. A sibling is any other
         * child of the same parent.
         *
         * @param matcher applied to the siblings in the parent's order, stopping at the first match.
         * @return a matcher whose description names this condition.
         */
        public fun hasAnySibling(matcher: SwingMatcher): SwingMatcher =
            SwingMatcher("hasAnySibling(${matcher.description})") { component ->
                component.siblingComponents().any(matcher::matches)
            }

        /**
         * Matches a component with at least one ancestor satisfying [matcher]. The whole parent
         * chain is considered, so this scopes a query to a subtree: it keeps the components that
         * sit anywhere below the container [matcher] describes.
         *
         * ```
         * onAllNodesOfType<JLabel>().filter(SwingMatcher.hasAnyAncestor(SwingMatcher.hasTestTag("editor")))
         * ```
         *
         * @param matcher applied to each ancestor in turn, nearest first.
         * @return a matcher whose description names this condition.
         */
        public fun hasAnyAncestor(matcher: SwingMatcher): SwingMatcher =
            SwingMatcher("hasAnyAncestor(${matcher.description})") { component ->
                component.ancestorComponents().any(matcher::matches)
            }

        /**
         * Matches a component with at least one descendant, at any depth, satisfying [matcher].
         *
         * @param matcher applied in depth-first pre-order; the component itself is not offered to it.
         * @return a matcher whose description names this condition.
         */
        public fun hasAnyDescendant(matcher: SwingMatcher): SwingMatcher =
            SwingMatcher("hasAnyDescendant(${matcher.description})") { component ->
                component.descendantComponents().any(matcher::matches)
            }

        @PublishedApi
        internal fun ofType(type: Class<out Component>): SwingMatcher =
            SwingMatcher("isOfType(${type.simpleName})") { type.isInstance(it) }

        internal fun isRoot(root: Container): SwingMatcher = SwingMatcher("isRoot") { it === root }

        /** Matches every component; the identity for narrowing combinators. */
        internal fun any(): SwingMatcher = SwingMatcher("any") { true }
    }
}
