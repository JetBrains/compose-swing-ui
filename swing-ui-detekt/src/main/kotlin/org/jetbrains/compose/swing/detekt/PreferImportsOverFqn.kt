// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

/**
 * Flags fully qualified type references that should be expressed with imports.
 *
 * Expression chains are deliberately ignored: PSI alone cannot distinguish a package root from a
 * value receiver, so reporting them would make valid member access fail the rule.
 */
internal class PreferImportsOverFqn(
    config: Config,
) : Rule(config, "Direct fully-qualified names should be replaced with imports.") {
    override val ruleName: RuleName = RuleName("PreferImportsOverFqn")

    override fun visitTypeReference(typeReference: KtTypeReference) {
        super.visitTypeReference(typeReference)
        val userType = typeReference.typeElement?.unwrapNullability() as? KtUserType ?: return
        val candidate = normalizeTypeCandidate(userType.text)
        if (!looksLikeDirectFqnType(candidate)) {
            return
        }
        reportViolation(entity = Entity.from(userType), text = candidate)
    }

    private fun reportViolation(
        entity: Entity,
        text: String,
    ) {
        report(
            Finding(
                entity = entity,
                message = "Use import instead of direct FQN: $text",
            ),
        )
    }

    private fun normalizeTypeCandidate(text: String): String =
        text
            .substringBefore('<')
            .trim()

    private fun looksLikeDirectFqnType(text: String): Boolean {
        val segments = text.split('.')
        val packagePrefixLength = lowercasePrefixLength(segments)
        val hasEnoughSegments = segments.size >= MIN_SEGMENTS_FOR_TYPE_FQN
        val hasNoBlankSegments = segments.none(String::isBlank)
        val typeSegments = segments.drop(packagePrefixLength)
        val hasPackagePrefix = packagePrefixLength > 0
        val hasTypeSegments = typeSegments.isNotEmpty()
        val typeSegmentsStartWithUppercase =
            typeSegments.all { it.firstOrNull()?.isUpperCase() == true }
        return hasEnoughSegments &&
            hasNoBlankSegments &&
            hasPackagePrefix &&
            hasTypeSegments &&
            typeSegmentsStartWithUppercase
    }

    private fun looksLikePackageToken(token: String): Boolean =
        token.firstOrNull()?.isLowerCase() == true &&
            token.all { char -> char == '_' || char.isLowerCase() || char.isDigit() }

    private fun lowercasePrefixLength(segments: List<String>): Int = segments.takeWhile(::looksLikePackageToken).size

    private companion object {
        private const val MIN_SEGMENTS_FOR_TYPE_FQN = 2
    }
}
