// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.androidx

import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.psi.KtExpression

/**
 * A function an expression calls, with the expression passed for each of its value parameters.
 */
internal class ArgumentedCall(
    val symbol: KaFunctionSymbol,
    val valueArgumentMapping: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
)
