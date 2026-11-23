// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.jetbrains.compose.swing.detekt.fixture.RestrictedLibrary
import java.nio.file.Path
import kotlin.test.assertIs

/** Lints [source] with the rule [ruleId] of [ComposeSwingInternalRuleSetProvider], the way detekt creates it. */
internal fun lintInternalRule(
    ruleId: String,
    source: String,
): List<Finding> =
    ComposeSwingInternalRuleSetProvider()
        .instance()
        .rules
        .getValue(RuleName(ruleId))(Config.empty)
        .lint(source)

internal fun composeSwingRule(ruleId: String): Rule = detektRuleFactory(ruleId)(Config.empty)

/**
 * The `RestrictedApi` rule configured the way the build configures it, with the compiled fixtures as a library.
 * [module] and [library] are each a `group:name`, or unknown if null.
 */
internal fun restrictedApiRule(
    module: String?,
    library: String?,
): AnalysisComposeSwingRuleAdapter {
    val settings =
        listOfNotNull(
            module?.let { "moduleCoordinates" to it },
            library?.let {
                val root =
                    Path.of(
                        RestrictedLibrary::class.java.protectionDomain.codeSource.location
                            .toURI(),
                    )
                "libraryCoordinates" to listOf("$root=$it")
            },
        )
    return assertIs<AnalysisComposeSwingRuleAdapter>(
        detektRuleFactory("RestrictedApi")(TestConfig(*settings.toTypedArray())),
    )
}
