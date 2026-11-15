// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.internal

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

/**
 * Flags fully qualified names that should be expressed with imports.
 *
 * Two shapes are reported. A type reference written with a package prefix, such as `java.io.File` or
 * `java.util.Map.Entry<K, V>`, is reported once, nullability and type arguments excluded. A dot-qualified
 * expression whose leftmost qualifier resolves to a package, such as `org.sample.node.SwingNode(...)` or
 * `java.awt.Color.RED`, is reported once per chain, at the package-qualified part (`java.awt.Color`), with the
 * qualified name in the message.
 *
 * Never reported: a chain whose qualifier is a value (a `val`, a parameter, `this`, an object), a qualifier or
 * selector that does not resolve, a package-qualified call whose package also declares an extension function of
 * the same name (the qualifier picks the overload), a qualified name in an `import` or `package` directive, and a
 * name inside a string literal.
 *
 * The expression check resolves names, so the rule runs only under the type-resolving tasks (`detektMain`,
 * `detektTest`, `detektTestFixtures`).
 */
internal object PreferImportsOverFqn : ComposeSwingRule {
    override val id: String = "PreferImportsOverFqn"
    override val description: String = "Direct fully-qualified names should be replaced with imports."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = false
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitTypeReference(typeReference: KtTypeReference) {
                val userType = typeReference.typeElement?.unwrapNullability() as? KtUserType ?: return
                val candidate = normalizeTypeCandidate(userType.text)
                if (!looksLikeDirectFqnType(candidate)) {
                    return
                }
                reporter.report(userType, "Use import instead of direct FQN: $candidate")
            }

            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                val inDirective =
                    PsiTreeUtil.getParentOfType(
                        expression,
                        KtImportDirective::class.java,
                        KtPackageDirective::class.java,
                    )
                if (inDirective != null) {
                    return
                }
                val selectorName = selectorName(expression) ?: return
                if (analyze(expression) { isPackageQualifiedMember(expression, selectorName) }) {
                    reporter.report(
                        expression,
                        "Use import instead of direct FQN: ${expression.receiverExpression.text}.${selectorName.text}",
                    )
                }
            }
        }

    private fun selectorName(expression: KtDotQualifiedExpression): KtNameReferenceExpression? =
        when (val selector = expression.selectorExpression) {
            is KtCallExpression -> selector.calleeExpression as? KtNameReferenceExpression
            is KtNameReferenceExpression -> selector
            else -> null
        }

    /** Whether the receiver of [expression] resolves to a package and its selector [name] to something else. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isPackageQualifiedMember(
        expression: KtDotQualifiedExpression,
        name: KtNameReferenceExpression,
    ): Boolean {
        val receiver = expression.receiverExpression
        val receiverName =
            ((receiver as? KtDotQualifiedExpression)?.selectorExpression ?: receiver) as? KtNameReferenceExpression
        val receiverSymbol = receiverName?.mainReference?.resolveToSymbols()?.firstOrNull()
        val selectorSymbol = name.mainReference.resolveToSymbols().firstOrNull()
        return receiverSymbol is KaPackageSymbol &&
            selectorSymbol != null &&
            selectorSymbol !is KaPackageSymbol &&
            !hasSameNamedExtension(expression, selectorSymbol)
    }

    /** Whether [expression] calls function [symbol] and its package declares an extension function named like it. */
    private fun KaSession.hasSameNamedExtension(
        expression: KtDotQualifiedExpression,
        symbol: KaSymbol,
    ): Boolean =
        expression.selectorExpression is KtCallExpression &&
            symbol is KaNamedFunctionSymbol &&
            symbol.callableId?.let { id ->
                findPackage(id.packageName)?.packageScope?.callables(id.callableName)?.any {
                    it is KaNamedFunctionSymbol && it.isExtension
                }
            } == true

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

    private const val MIN_SEGMENTS_FOR_TYPE_FQN = 2
}
