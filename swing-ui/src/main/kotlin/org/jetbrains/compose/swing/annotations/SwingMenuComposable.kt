package org.jetbrains.compose.swing.annotations

import androidx.compose.runtime.ComposableTargetMarker

/**
 * Marks a composable that emits into a menu tree.
 *
 * A menu holds menu items and separators rather than components, so it is composed under its own
 * marker and the compiler rejects a call that would mix the two.
 *
 * @see SwingComposable
 */
@Retention(AnnotationRetention.BINARY)
@ComposableTargetMarker(description = "Swing Menu Composable")
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
)
public annotation class SwingMenuComposable
