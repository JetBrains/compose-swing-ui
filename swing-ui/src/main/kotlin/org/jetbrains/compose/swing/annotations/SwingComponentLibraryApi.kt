// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.annotations

/**
 * Marks API for writing components. A component library wraps each Swing component class in its own
 * composable, and layout code uses those composables instead of building components itself.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message =
        "This API is for writing components, not for building layouts. A Swing component placed directly in a " +
            "layout keeps its layout, state and listeners out of the composition's reach. In layout code, use " +
            "component composables instead. To use a Swing component that has no composable yet, wrap it in " +
            "its own composable.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class SwingComponentLibraryApi
