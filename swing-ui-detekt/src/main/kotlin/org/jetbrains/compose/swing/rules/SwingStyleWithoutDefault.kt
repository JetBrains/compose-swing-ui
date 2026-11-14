// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

/**
 * Reports a composable whose `SwingStyle` parameter does not default to the empty style.
 *
 * A caller that has nothing to declare should not have to say so. The parameter defaults to the empty
 * style, the companion of `SwingStyle`, as a `SwingModifier` parameter defaults to the empty chain.
 *
 * Only a declaration a caller outside its module can reach is checked, as in [SwingModifierWithoutDefault].
 */
internal object SwingStyleWithoutDefault : ComposeSwingRule {
    override val id: String = "SwingStyleWithoutDefault"
    override val description: String = "A composable's `SwingStyle` parameter should default to the empty style"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (!function.isComposable() || !function.isReachableFromOutside() ||
                    function.isImplementingSomething()
                ) {
                    return
                }
                function.valueParameters
                    .filter { it.typeReference.declaresSwingStyle() }
                    .forEach(::reportUnlessDefaultsToTheEmptyStyle)
            }

            private fun reportUnlessDefaultsToTheEmptyStyle(parameter: KtParameter) {
                val default = parameter.defaultValue
                if (default == null) {
                    reporter.report(parameter, noDefaultMessage(parameter.name))
                    return
                }
                if (default.isLibraryCompanionDefault(SWING_STYLE_FQN)) return
                reporter.report(default, wrongDefaultMessage(parameter.name, default.text))
            }
        }
}

private fun KtTypeReference?.declaresSwingStyle(): Boolean =
    (this?.typeElement?.unwrapNullability() as? KtUserType)?.declaresLibraryType(SWING_STYLE_FQN) == true

private const val SWING_STYLE: String = "SwingStyle"

private const val SWING_STYLE_FQN: String = "org.jetbrains.compose.swing.style.$SWING_STYLE"

private fun noDefaultMessage(name: String?): String =
    "The `$name` parameter has no default. A caller that sets no style " +
        "must still pass one. Add `= $SWING_STYLE` as the default."

private fun wrongDefaultMessage(
    name: String?,
    defaultText: String,
): String =
    "The `$name` parameter defaults to `$defaultText` instead of the empty " +
        "style. Every caller that leaves it out gets this style without asking for it. " +
        "Default it to the `$SWING_STYLE` companion."
