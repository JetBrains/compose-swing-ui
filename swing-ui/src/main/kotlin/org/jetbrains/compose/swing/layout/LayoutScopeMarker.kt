package org.jetbrains.compose.swing.layout

/**
 * Marks layout scope receivers so an extension from one container scope is unavailable inside another.
 *
 * Apply this to a custom container scope that declares modifier extensions for its children; nested
 * layout scopes then cannot accidentally use those extensions for the wrong parent.
 */
@DslMarker
public annotation class LayoutScopeMarker
