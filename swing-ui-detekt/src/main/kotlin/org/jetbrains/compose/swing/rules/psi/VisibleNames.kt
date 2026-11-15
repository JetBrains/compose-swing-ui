// Copyright 2024 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Adapted to the library's host-neutral rule API.

package org.jetbrains.compose.swing.rules.psi

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal fun KtCallExpression.hasVisibleNamesake(name: String): Boolean =
    containingKtFile.declarations.any { it.name == name } ||
        parents.any { owner ->
            when (owner) {
                is KtBlockExpression -> {
                    owner.statements.any { it.endOffset <= startOffset && it.declaresName(name) }
                }

                is KtClassOrObject -> {
                    owner.declaresMember(name)
                }

                is KtFunction -> {
                    owner.name == name || owner.valueParameters.any { it.name == name }
                }

                else -> {
                    false
                }
            }
        }

private fun KtClassOrObject.declaresMember(name: String): Boolean =
    declarations.any { it.name == name } ||
        primaryConstructorParameters.any { it.name == name } ||
        companionObjects.any { companion -> companion.declarations.any { it.name == name } }

private fun KtExpression.declaresName(name: String): Boolean =
    when (this) {
        is KtNamedDeclaration -> this.name == name
        is KtDestructuringDeclaration -> entries.any { it.name == name }
        else -> false
    }
