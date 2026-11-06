/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Ported from androidx's ModifierNodeInspectablePropertiesDetector (frameworks/support/compose/ui/
// ui-lint/src/main/java/androidx/compose/ui/lint/ModifierNodeInspectablePropertiesDetector.kt), an
// Android Lint UastScanner, to a detekt Rule. The counterpart of upstream's `ModifierNodeElement` and
// its `InspectorInfo.inspectableProperties()` is `SwingModifier.InspectableElement` and its two open
// members, `name` and `declaredValues`, so the following are substituted or dropped:
//  - The self-description an element may declare is two properties rather than one function, so an
//    element overriding either one is satisfied, and only one overriding neither is reported.
//    Upstream's two "almost overrides it" cases - a wrong parameter list, a missing receiver - become
//    the one case a property has: a member of that name that is not an override of the interface's,
//    which is any declaration outside the class's own body or primary constructor.
//  - Upstream matches its supertype by resolved fully qualified name and reaches every subclass however
//    deep. This rule requires a declaration's direct supertype to be qualified by the library
//    `SwingModifier`, including an import alias for it. An unqualified `InspectableElement` supertype,
//    a type alias or an intermediate type is not seen; what the rule cannot identify safely, it skips.
//  - An element built from nothing is skipped, where upstream reports every element. Both defaults are
//    correct for it: there is no value to declare, and the class name is the whole of what it is. Only an
//    element carrying state a reader declared - a constructor parameter that is not a callback - is
//    reported.
//  - Interfaces and abstract classes are skipped, where upstream reports them. Neither can be an entry
//    on a modifier, and the concrete classes below them carry their own overrides.

package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

/**
 * Reports a modifier element that describes itself to nothing: one implementing
 * `SwingModifier.InspectableElement` that overrides neither `name` nor `declaredValues`.
 *
 * Both members carry a default, so such an element compiles and a tool showing the modifier a component
 * carries reads the class name off it and no declared values at all. Overriding one of them says what the
 * entry is called, or what it declares, in the one place a tool can read it.
 *
 * An element carrying no state a reader declared is passed over: its class name is the whole of what it
 * is, and it has no values to name. So is an interface or an abstract class - neither is an entry on a
 * modifier, and the classes below it declare their own.
 *
 * Only direct supertypes qualified by the library `SwingModifier` or its import alias are matched. This
 * avoids treating an unrelated type with the same simple name as this library's element contract.
 */
public class ModifierNodeInspectableProperties(
    config: Config,
) : Rule(config, "A modifier element should describe itself with a name or the values it declares.") {
    override val ruleName: RuleName = RuleName("ModifierNodeInspectableProperties")

    private companion object {
        val SELF_DESCRIPTION: Set<String> = setOf("name", "declaredValues")

        /** Supertypes that directly provide the inspectable element contract. */
        val INSPECTABLE_SUPERTYPES: Set<String> = setOf("InspectableElement", "NodeElement")
    }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        if (!classOrObject.isAnInspectableElement() || !classOrObject.carriesDeclaredState()) return
        if (classOrObject.describesItself()) return
        report(
            Finding(
                entity = Entity.atName(classOrObject),
                message =
                    "`${classOrObject.name ?: "The element"}` overrides neither `name` nor " +
                        "`declaredValues`, so a tool showing the modifier a component carries reads the " +
                        "class name off it and none of what it declares. Override `name` where the class " +
                        "name is not what the entry is called, and `declaredValues` with what it carries.",
            ),
        )
    }

    private fun KtClassOrObject.isAnInspectableElement(): Boolean {
        val declaresAnEntry =
            when (this) {
                is KtClass -> !isInterface() && !hasModifier(KtTokens.ABSTRACT_KEYWORD)
                else -> true
            }
        return declaresAnEntry &&
            superTypeListEntries.any {
                val type = it.typeReference?.typeElement as? KtUserType
                type?.referencedName in INSPECTABLE_SUPERTYPES && type?.isDirectlyQualifiedBySwingModifier() == true
            }
    }

    private fun KtUserType.isDirectlyQualifiedBySwingModifier(): Boolean = qualifier?.declaresLibraryType() == true

    /**
     * Whether [this] carries state a reader declared: a primary constructor parameter that is not a callback.
     * A lambda renders as its own class, so it is nothing to show and nothing to hold an element to.
     */
    private fun KtClassOrObject.carriesDeclaredState(): Boolean {
        val parameters = primaryConstructor?.valueParameters ?: return false
        return parameters.any { it.typeReference?.typeElement?.unwrapNullability() !is KtFunctionType }
    }

    /**
     * Whether [this] overrides either member of the interface, in its own body or as a property of its primary
     * constructor - the form a shared element seam takes, where the name arrives as an argument.
     *
     * Only the declarations the class itself makes count: a nested class or a companion declaring a `name` of
     * its own leaves the element's still standing at the default.
     */
    private fun KtClassOrObject.describesItself(): Boolean =
        declarations.filterIsInstance<KtProperty>().any { it.overridesSelfDescription() } ||
            primaryConstructor?.valueParameters?.any { it.hasValOrVar() && it.overridesSelfDescription() } == true

    private fun KtNamedDeclaration.overridesSelfDescription(): Boolean =
        hasModifier(KtTokens.OVERRIDE_KEYWORD) && name in SELF_DESCRIPTION
}
