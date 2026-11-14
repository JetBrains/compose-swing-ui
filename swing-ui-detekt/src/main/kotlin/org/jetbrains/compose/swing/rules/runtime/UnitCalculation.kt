// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtLambdaExpression

/** Whether [lambda] yields `Unit`: an empty body does, and so does a last statement that resolves to `Unit`. */
internal fun KaSession.yieldsUnit(lambda: KtLambdaExpression): Boolean {
    val last = lambda.bodyExpression?.statements?.lastOrNull() ?: return true
    return (last.expressionType?.fullyExpandedType as? KaClassType)?.classId == StandardClassIds.Unit
}
