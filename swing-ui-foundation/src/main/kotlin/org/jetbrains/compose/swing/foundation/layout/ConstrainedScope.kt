package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.LayoutScopeMarker

/**
 * The scope of a container whose child stands between the constraints that container offers it and
 * its own measurement: a fill, a padding, an offset, an aspect ratio or a default minimum size each
 * narrows what reaches the child, states what the child plus its own space occupies, and places the child
 * inside that.
 *
 * [Row], [Column] and [Box] offer this to their content, so a child of any of them declares these alongside
 * what that container's own scope offers, and a custom [Layout] offers this alone. A modifier of the
 * caller's own is built as a top-level extension taking [ConstrainedScope] as a context parameter.
 */
@LayoutScopeMarker
public interface ConstrainedScope
