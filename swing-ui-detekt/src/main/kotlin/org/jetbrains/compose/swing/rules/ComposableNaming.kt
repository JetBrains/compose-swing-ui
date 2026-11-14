// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.compose.swing.rules.runtime.ComposableReturnTypeNaming
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a composable whose name contradicts what it returns: a lowercase name on a function that returns `Unit`,
 * or an uppercase name on one that returns a value.
 * Runs compose-rules' `Naming` check, which reads the declared return type, keeps its findings only where the
 * resolved return type agrees, and adds the functions whose resolved return type differs from the declared
 * one, such as an expression body or an extension function.
 */
internal object ComposableNaming : ComposeSwingRule {
    override val id = "ComposableNaming"
    override val description =
        "Composable functions that return Unit should start with an uppercase letter. " +
            "They are considered declarative entities that can be either present or absent in a composition " +
            "and therefore follow the naming rules for classes. However, Composable functions that return " +
            "a value should start with a lowercase letter instead. They should follow the standard Kotlin " +
            "Coding Conventions for the naming of functions for any function annotated @Composable that " +
            "returns a value other than Unit."
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = ComposableReturnTypeNaming(reporter, config)
}
