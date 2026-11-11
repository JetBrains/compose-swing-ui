// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics.drawscope

/**
 * Marks declarations that belong to the [DrawScope] drawing DSL, preventing nested scopes
 * from calling into outer scope drawing operations without explicit qualification.
 */
@DslMarker
public annotation class DrawScopeMarker
