// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.annotations

/**
 * Marks Swing-UI runtime declarations that are an internal implementation detail rather than stable
 * public API.
 *
 * Such a declaration is Kotlin-`public` only for technical reasons and is not intended for use by
 * applications. It may change or be removed without notice in any release. Opt in with
 * `@OptIn(InternalSwingUiApi::class)` only when you are deliberately integrating at this level and
 * accept that risk.
 */
@RequiresOptIn(
    message =
        "This is an internal Swing-UI runtime declaration, not part of the stable public API. " +
            "It may change or be removed without notice in any release.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalSwingUiApi
