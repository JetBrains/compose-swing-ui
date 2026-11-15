// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.psi

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Whether a caller outside this library can reach [this]: neither it nor any declaration around it is
 * private or internal, and it is not local.
 *
 * A local property cannot declare a getter, so a property that reaches this check is never local;
 * locality is checked on a function alone.
 */
internal fun KtCallableDeclaration.isReachableFromOutside(): Boolean =
    (this !is KtNamedFunction || !isLocal) &&
        !isHiddenFromOutside() &&
        parents.filterIsInstance<KtClassOrObject>().none { it.isLocal || it.isHiddenFromOutside() }

private fun KtModifierListOwner.isHiddenFromOutside(): Boolean =
    hasModifier(KtTokens.PRIVATE_KEYWORD) || hasModifier(KtTokens.INTERNAL_KEYWORD)

/**
 * Whether a consumer outside this module can name [this]: neither it nor any class or object it is
 * declared inside is `private` or `internal`. A companion is a container like any other, so a data class
 * inside the companion of a public class is reachable.
 */
internal fun KtClassOrObject.isReachableFromOutside(): Boolean =
    generateSequence(this) { it.getStrictParentOfType<KtClassOrObject>() }
        .none { it.hasModifier(KtTokens.PRIVATE_KEYWORD) || it.hasModifier(KtTokens.INTERNAL_KEYWORD) }
