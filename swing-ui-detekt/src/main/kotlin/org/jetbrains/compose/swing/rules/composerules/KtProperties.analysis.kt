// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.compose.swing.rules.composerules

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtProperty

internal fun KtProperty.hasComposeStateDelegate(): Boolean = runCatching {
    val delegate = delegateExpression ?: return@runCatching false

    analyze(delegate) {
        val delegateType = delegate.expressionType ?: return@analyze false
        delegateType.symbol?.classId?.isComposeState(ComposeStateNames) == true ||
            delegateType.allSupertypes.any { supertype ->
                supertype.symbol?.classId?.isComposeState(ComposeStateNames) == true
            }
    }
}.getOrDefault(false)

internal fun ClassId.isComposeState(stateNames: Set<String>): Boolean =
    packageFqName == ComposeFqNames.runtime && relativeClassName.asString() in stateNames

private val ComposeStateNames = setOf(
    "State",
    "MutableState",
    "IntState",
    "MutableIntState",
    "LongState",
    "MutableLongState",
    "FloatState",
    "MutableFloatState",
    "DoubleState",
    "MutableDoubleState",
)
