// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.RuleName
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import kotlin.test.assertIs

/** Fixtures resolve against the test runtime classpath, including the Compose runtime and coroutines artifacts.
 * They also resolve against the compiled testLibrary output.
 */
internal object RuntimeRuleLint {
    private val environment = createEnvironment()

    fun lint(
        ruleId: String,
        source: String,
        vararg dependencies: String,
    ): List<Finding> =
        assertIs<AnalysisComposeSwingRuleAdapter>(
            ComposeRuntimeRuleSetProvider()
                .instance()
                .rules
                .getValue(RuleName(ruleId))(Config.empty),
        ).lintWithContext(environment, source, *dependencies)
}
