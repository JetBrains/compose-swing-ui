package org.jetbrains.compose.swing.annotations

import androidx.compose.runtime.ComposableTargetMarker

/**
 * Marks a composable that emits into a tree of Swing components.
 *
 * The compiler rejects a call that would emit into a tree of another kind, so component content and
 * menu content cannot be composed into each other by mistake.
 *
 * It belongs on a composable that emits a component itself and on a `content` lambda parameter handed
 * on to one; a composable that only calls others carries no marker of its own.
 *
 * @see SwingMenuComposable
 */
@Retention(AnnotationRetention.BINARY)
@ComposableTargetMarker(description = "Swing Composable")
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
)
public annotation class SwingComposable
