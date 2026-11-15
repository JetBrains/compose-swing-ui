// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.androidx

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * Whether [entry] resolves to a `VisibleForTesting` or `TestOnly` annotation class of any package, as
 * androidx's check matches the resolved qualified name rather than the name written at the use site, so
 * an import alias neither hides one of them nor makes another annotation look like one. An annotation
 * that does not resolve matches neither.
 */
internal fun KaSession.isTestContextAnnotation(entry: KtAnnotationEntry): Boolean {
    val qualifiedName =
        entry.typeReference
            ?.type
            ?.symbol
            ?.classId
            ?.asSingleFqName()
            ?.asString() ?: return false
    return listOf(".VisibleForTesting", ".TestOnly").any(qualifiedName::endsWith)
}
