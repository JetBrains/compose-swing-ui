// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

/**
 * Reports a composable whose `SwingStyle` parameter does not default to the empty style.
 *
 * A caller that has nothing to declare should not have to say so. The parameter defaults to the empty
 * style, the companion of `SwingStyle`, as a `SwingModifier` parameter defaults to the empty chain.
 *
 * Only a declaration a caller outside its module can reach is checked, as in [SwingModifierWithoutDefault].
 */
public class SwingStyleWithoutDefault(
    config: Config,
) : Rule(config, "A composable's `SwingStyle` parameter should default to the empty style") {
    override val ruleName: RuleName = RuleName("SwingStyleWithoutDefault")

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.isComposable() || !function.isReachableFromOutside() || function.isImplementingSomething()) {
            return
        }
        function.valueParameters
            .filter { it.typeReference.declaresSwingStyle() }
            .forEach(::reportUnlessDefaultsToTheEmptyStyle)
    }

    private fun reportUnlessDefaultsToTheEmptyStyle(parameter: KtParameter) {
        val default = parameter.defaultValue
        if (default == null) {
            report(
                Finding(
                    entity = Entity.from(parameter),
                    message = noDefaultMessage(parameter.name),
                ),
            )
            return
        }
        if (default.isLibraryCompanionDefault(SWING_STYLE_FQN)) return
        report(
            Finding(
                entity = Entity.from(default),
                message = wrongDefaultMessage(parameter.name, default.text),
            ),
        )
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
