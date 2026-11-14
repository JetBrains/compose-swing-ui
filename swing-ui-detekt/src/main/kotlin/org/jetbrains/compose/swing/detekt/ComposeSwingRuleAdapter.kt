// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import com.intellij.psi.PsiElement
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.PublicDataClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/**
 * Runs a [ComposeSwingRule] as a detekt rule: it walks the file once and hands every element to the
 * rule's visitor, and turns each report into a finding.
 *
 * A report on a declaration's name identifier is signed as its declaration, which is what `Entity.atName`
 * signs. [PublicDataClass] alone is signed as the identifier itself, as its detekt rule always signed it,
 * so a baseline written then still matches.
 *
 * detekt reads a rule's severity from its configuration. `config.yml` sets it only where it differs from
 * detekt's default of error, and [ComposeSwingRule.severity] is the value it carries.
 */
internal open class ComposeSwingRuleAdapter(
    config: Config,
    rule: ComposeSwingRule,
) : Rule(config, rule.description) {
    override val ruleName: RuleName = RuleName(rule.id)

    private val visitor =
        rule.createVisitor(
            reporter = { element, message ->
                val declaration = (element.parent as? KtNamedDeclaration)?.takeIf { rule.id != PublicDataClass.id }
                val entity =
                    if (declaration != null && declaration.nameIdentifier === element) {
                        Entity.from(element, declaration)
                    } else {
                        Entity.from(element)
                    }
                report(Finding(entity, message))
            },
            config = DetektRuleConfig(config),
        )

    override fun visitElement(element: PsiElement) {
        element.accept(visitor)
        super.visitElement(element)
    }
}

/** The adapter for a rule that resolves symbols or types, which detekt runs only with the Analysis API. */
internal class AnalysisComposeSwingRuleAdapter(
    config: Config,
    rule: ComposeSwingRule,
) : ComposeSwingRuleAdapter(config, rule),
    RequiresAnalysisApi

private class DetektRuleConfig(
    private val config: Config,
) : ComposeSwingRuleConfig {
    override fun <T> valueOrDefault(
        key: String,
        default: T,
    ): T = default?.let { config.valueOrDefault(key, it) } ?: default

    override fun valueOrDefault(
        key: String,
        default: List<String>,
    ): List<String> =
        try {
            config.valueOrDefault(key, default)
        } catch (_: ClassCastException) {
            separatedNames(key, default)
        } catch (_: IllegalStateException) {
            separatedNames(key, default)
        }

    private fun separatedNames(
        key: String,
        default: List<String>,
    ): List<String> =
        config
            .valueOrDefault(key, default.joinToString(","))
            .split(',', ';')
            .map(String::trim)
            .filter(String::isNotEmpty)
}

/** Creates the detekt rule that runs this rule, one per configuration detekt hands over. */
internal fun ComposeSwingRule.toDetektRule(config: Config): Rule =
    if (requiresAnalysisApi) AnalysisComposeSwingRuleAdapter(config, this) else ComposeSwingRuleAdapter(config, this)

/** The detekt rule factories for [rules], keyed by rule name. */
internal fun adaptedRules(rules: List<ComposeSwingRule>): Map<RuleName, (Config) -> Rule> =
    rules.associate { rule -> RuleName(rule.id) to { config: Config -> rule.toDetektRule(config) } }
