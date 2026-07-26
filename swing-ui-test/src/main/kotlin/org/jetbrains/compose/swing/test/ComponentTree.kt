package org.jetbrains.compose.swing.test

import java.awt.Component
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.text.JTextComponent

/**
 * Returns the textual content of [this] component for matching purposes, or `null` if the component
 * type has no associated text.
 */
internal fun Component.textOrNull(): String? =
    when (this) {
        is JLabel -> text
        is AbstractButton -> text
        is JTextComponent -> text
        else -> null
    }

/**
 * The direct children of [this] component, in the order its container holds them; empty for a
 * component that holds none. Must be called on the EDT.
 */
internal fun Component.childComponents(): List<Component> = (this as? Container)?.components?.toList().orEmpty()

/**
 * Every other child of [this] component's parent, in the parent's order; empty when it has no parent.
 * Must be called on the EDT.
 */
internal fun Component.siblingComponents(): List<Component> =
    parent?.childComponents()?.filter { it !== this }.orEmpty()

/**
 * The parent chain of [this] component, nearest first, up to the top of the tree it is attached to.
 * Must be called on the EDT.
 */
internal fun Component.ancestorComponents(): Sequence<Component> = generateSequence(parent) { it.parent }

/**
 * Every component below [this] one, in depth-first pre-order and excluding [this] itself. Must be
 * called on the EDT.
 */
internal fun Component.descendantComponents(): Sequence<Component> =
    sequence {
        for (child in childComponents()) {
            yield(child)
            yieldAll(child.descendantComponents())
        }
    }

/**
 * Recursively collects every component in the subtree rooted at [this] (excluding [this] itself)
 * that satisfies [matcher], in depth-first pre-order. Must be called on the EDT.
 *
 * Walks the real AWT tree via [Container.getComponents].
 */
internal fun Container.findMatching(matcher: SwingMatcher): List<Component> =
    descendantComponents().filter(matcher::matches).toList()

/**
 * Collects [this] container (when it matches) and every matching descendant, in depth-first
 * pre-order. Must be called on the EDT.
 */
internal fun Container.findMatchingIncludingSelf(matcher: SwingMatcher): List<Component> {
    val self = if (matcher.matches(this)) listOf<Component>(this) else emptyList()
    return self + findMatching(matcher)
}

/**
 * Renders the subtree rooted at [this] as an indented, readable string for failure messages.
 * Must be called on the EDT.
 *
 * The dump is bounded so a deep or wide tree cannot flood a failure message: at most
 * [MAX_DUMP_DEPTH] levels deep and [MAX_DUMP_LINES] lines. Whatever is elided is replaced by a
 * single `(truncated ...)` marker so the reader knows the structure continues, while the top of the
 * tree - the part that usually identifies the defect - is always preserved.
 */
internal fun Container.dumpTree(): String {
    val dump = BoundedTreeDump()
    dump.visit(this, depth = 0)
    return dump.finish()
}

/**
 * One-line description of [component] - its type plus the state a reader identifies it by - for
 * failure messages. Must be called on the EDT.
 */
internal fun describeComponent(component: Component): String {
    val type = component.javaClass.simpleName.ifEmpty { component.javaClass.name }
    val text = component.textOrNull()?.let { " text=\"$it\"" }.orEmpty()
    val name = component.name?.let { " name=\"$it\"" }.orEmpty()
    val context = component.accessibleContext
    val accessibleName = context?.accessibleName?.let { " a11yName=\"$it\"" }.orEmpty()
    val accessibleDescription = context?.accessibleDescription?.let { " a11yDesc=\"$it\"" }.orEmpty()
    val enabled = if (component.isEnabled) "" else " disabled"
    return "$type$text$name$accessibleName$accessibleDescription$enabled"
}

/**
 * Accumulates an indented tree dump while enforcing the [MAX_DUMP_DEPTH] / [MAX_DUMP_LINES] bounds.
 */
private class BoundedTreeDump {
    private val sb = StringBuilder()
    private var lines = 0
    private var truncated = false

    fun visit(
        component: Component,
        depth: Int,
    ) {
        if (truncated) return
        if (lines >= MAX_DUMP_LINES) {
            truncated = true
            return
        }
        appendLine(depth, describeComponent(component))
        val children = component.childComponents()
        when {
            children.isEmpty() -> Unit
            depth + 1 > MAX_DUMP_DEPTH -> appendLine(depth + 1, "(truncated: deeper levels omitted)")
            else -> for (child in children) visit(child, depth + 1)
        }
    }

    fun finish(): String {
        if (truncated) sb.append("(truncated: tree exceeds $MAX_DUMP_LINES lines)\n")
        return sb.toString()
    }

    private fun appendLine(
        depth: Int,
        text: String,
    ) {
        sb.append("  ".repeat(depth)).append(text).append('\n')
        lines++
    }
}

// Bounds for [dumpTree]: keep the structurally useful top of the tree, drop the rest behind a
// "(truncated)" marker rather than flooding a failure message with thousands of lines.
private const val MAX_DUMP_DEPTH: Int = 4
private const val MAX_DUMP_LINES: Int = 100
