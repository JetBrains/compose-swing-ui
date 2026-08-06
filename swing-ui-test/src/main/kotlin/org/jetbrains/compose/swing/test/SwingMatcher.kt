package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.modifier.appearance.TEST_TAG_CLIENT_PROPERTY_KEY
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Frame
import javax.accessibility.AccessibleRole
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JComponent
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
    /** Evaluates this matcher against [component]. Must be called on the EDT. */
    public fun matches(component: Component): Boolean = predicate(component)

    /** Returns a matcher satisfied only when both this and [other] match. */
    public infix fun and(other: SwingMatcher): SwingMatcher =
        SwingMatcher("($description && ${other.description})") {
            predicate(it) && other.matches(it)
        }

    /** Returns a matcher satisfied when this or [other] matches. */
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
         * [JTextComponent.getText] depending on the component type.
         */
        public fun hasText(
            text: String,
            substring: Boolean = false,
        ): SwingMatcher {
            val desc = if (substring) "hasText(substring=\"$text\")" else "hasText(\"$text\")"
            return SwingMatcher(desc) { component ->
                val actual = component.textOrNull() ?: return@SwingMatcher false
                if (substring) actual.contains(text) else actual == text
            }
        }

        /** Matches a component whose [Component.getName] equals [name]. */
        public fun hasName(name: String): SwingMatcher = SwingMatcher("hasName(\"$name\")") { it.name == name }

        /** Matches a component tagged with [tag] via `SwingModifier.testTag`. */
        public fun hasTestTag(tag: String): SwingMatcher =
            SwingMatcher("hasTestTag(\"$tag\")") { component ->
                component is JComponent &&
                    component.getClientProperty(TEST_TAG_CLIENT_PROPERTY_KEY) == tag
            }

        /**
         * Matches a component whose accessible name equals [name], read from its
         * [java.awt.Component.getAccessibleContext].
         */
        public fun hasAccessibleName(name: String): SwingMatcher =
            SwingMatcher("hasAccessibleName(\"$name\")") { component ->
                component.accessibleContext?.accessibleName == name
            }

        /**
         * Matches a component whose accessible description equals [description], read from its
         * [java.awt.Component.getAccessibleContext].
         */
        public fun hasAccessibleDescription(description: String): SwingMatcher =
            SwingMatcher("hasAccessibleDescription(\"$description\")") { component ->
                component.accessibleContext?.accessibleDescription == description
            }

        /**
         * Matches a component whose accessible role equals [role], read from its
         * [java.awt.Component.getAccessibleContext].
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
         */
        public fun hasTitle(title: String): SwingMatcher =
            SwingMatcher("hasTitle(\"$title\")") { component ->
                when (component) {
                    is Frame -> component.title == title
                    is Dialog -> component.title == title
                    is JInternalFrame -> component.title == title
                    else -> false
                }
            }

        /** Matches a component whose enabled state equals [enabled]. */
        public fun isEnabled(enabled: Boolean = true): SwingMatcher =
            SwingMatcher("isEnabled($enabled)") { it.isEnabled == enabled }

        /**
         * Matches a component whose selected state equals [selected], read from
         * [AbstractButton.isSelected] - the state a check box, radio button, toggle button or
         * checkable menu item carries. A component that carries no selected state never matches, in
         * either direction, so `isSelected(false)` asserts "carries a selection and is off" while
         * `!isSelected()` also admits a component that cannot be selected at all.
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
         */
        public fun hasParent(matcher: SwingMatcher): SwingMatcher =
            SwingMatcher("hasParent(${matcher.description})") { component ->
                component.parent?.let(matcher::matches) == true
            }

        /** Matches a component with at least one direct child satisfying [matcher]. */
        public fun hasAnyChild(matcher: SwingMatcher): SwingMatcher =
            SwingMatcher("hasAnyChild(${matcher.description})") { component ->
                component.childComponents().any(matcher::matches)
            }

        /**
         * Matches a component with at least one sibling satisfying [matcher]. A sibling is any other
         * child of the same parent.
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
         */
        public fun hasAnyAncestor(matcher: SwingMatcher): SwingMatcher =
            SwingMatcher("hasAnyAncestor(${matcher.description})") { component ->
                component.ancestorComponents().any(matcher::matches)
            }

        /** Matches a component with at least one descendant, at any depth, satisfying [matcher]. */
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
