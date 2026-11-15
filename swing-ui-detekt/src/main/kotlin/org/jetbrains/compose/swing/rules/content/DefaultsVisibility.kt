// Copyright 2023-2026 Nacho Lopez, JetBrains s.r.o., and contributors.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.compose.swing.rules.content

import io.nlopez.compose.core.ComposeKtConfig
import io.nlopez.compose.core.ComposeKtVisitor
import io.nlopez.compose.core.Emitter
import io.nlopez.compose.core.report
import io.nlopez.compose.core.util.findAllChildrenByClass
import io.nlopez.compose.core.util.isInternal
import io.nlopez.compose.core.util.isPrivate
import io.nlopez.compose.core.util.isProtected
import org.jetbrains.compose.swing.rules.core.modifier.isComposable
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.isPublic

/** The compose-rules check for defaults declarations that are less visible than the composables that use them. */
internal class DefaultsVisibility : ComposeKtVisitor {
    override fun visitClassOrObject(
        clazz: KtClassOrObject,
        emitter: Emitter,
        config: ComposeKtConfig,
    ) {
        val defaultsName = clazz.name.orEmpty()
        val composableName = defaultsName.removeSuffix("Defaults")
        if (composableName.isEmpty() || composableName == defaultsName) return

        val required =
            clazz.containingKtFile
                .findAllChildrenByClass<KtFunction>()
                .filter { it.isComposable() && it.name == composableName && it.referencesDefaults(defaultsName) }
                .maxOfOrNull { it.defaultsVisibility } ?: return
        val actual = clazz.defaultsVisibility
        if (actual < required) {
            emitter.report(clazz, defaultsVisibilityMessage(defaultsName, composableName, actual, required))
        }
    }
}

private fun KtFunction.referencesDefaults(name: String): Boolean {
    val expressions = valueParameters.asSequence().map { it.defaultValue } + bodyBlockExpression
    return expressions.filterNotNull().any { expression ->
        expression.findAllChildrenByClass<KtReferenceExpression>().any { it.text == name }
    }
}

// Internal visibility ranks above protected visibility when comparing declarations across overloads.
private enum class DefaultsAccess {
    Unknown,
    Private,
    Protected,
    Internal,
    Public,
}

private val KtModifierListOwner.defaultsVisibility: DefaultsAccess
    get() =
        when {
            isPublic -> DefaultsAccess.Public
            isInternal -> DefaultsAccess.Internal
            isProtected -> DefaultsAccess.Protected
            isPrivate -> DefaultsAccess.Private
            else -> DefaultsAccess.Unknown
        }

private fun defaultsVisibilityMessage(
    defaultsName: String,
    composableName: String,
    actual: DefaultsAccess,
    required: DefaultsAccess,
): String =
    "`$defaultsName` has ${actual.name.lowercase()} visibility. The most visible `$composableName` " +
        "composable that uses it is ${required.name.lowercase()}, so some callers cannot access its defaults. " +
        "Make `$defaultsName` ${required.name.lowercase()}."
