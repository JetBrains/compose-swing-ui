package org.jetbrains.compose.swing.foundation.graphics.drawscope

/**
 * Marks declarations that belong to the [DrawScope] drawing DSL, preventing nested scopes
 * from calling into outer scope drawing operations without explicit qualification.
 */
@DslMarker
public annotation class DrawScopeMarker
