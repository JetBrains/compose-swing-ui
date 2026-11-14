// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.RuleName
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment

internal object ComposeSwingRuleLint {
    private val environment = createEnvironment()

    fun lint(
        id: String,
        source: String,
        config: Config = Config.empty,
        vararg dependencyContents: String,
    ): List<Finding> {
        val rule = ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName(id))(config)
        require(rule is RequiresAnalysisApi)
        return rule.lintWithContext(environment, source, dependencyContents = arrayOf(*dependencyContents))
    }
}
