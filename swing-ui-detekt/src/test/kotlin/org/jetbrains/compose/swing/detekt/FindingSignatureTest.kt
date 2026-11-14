// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

/**
 * A detekt baseline keys a finding by its rule and signature, so a signature that changes resurfaces
 * accepted findings with no source change. Each case states the signature the rule's detekt `Rule`
 * built before rules moved to the shared adapter.
 */
class FindingSignatureTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `a finding keeps the signature its rule has always had`(
        label: String,
        lint: (String) -> List<Finding>,
        source: String,
        signatures: List<String>,
    ) {
        assertEquals(signatures, lint(source).map { it.entity.signature }, label)
    }

    companion object {
        private fun internalRule(id: String): (String) -> List<Finding> = { lintInternalRule(id, it) }

        private fun case(
            label: String,
            lint: (String) -> List<Finding>,
            source: String,
            vararg signatures: String,
        ): Arguments = Arguments.of(label, lint, "package sample\n\n$source", signatures.toList())

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                case(
                    "PublicDataClass names the class identifier it reports",
                    internalRule("PublicDataClass"),
                    "data class Foo(val n: Int)",
                    "Foo\$Foo",
                ),
                case(
                    "PublicDataClass names a nested class by its outer class",
                    internalRule("PublicDataClass"),
                    "class Holder {\n    data class Row(val v: Int)\n}",
                    "Holder.Row\$Row",
                ),
                case(
                    "ModifierNodeInspectableProperties names the declaration",
                    {
                        ComposeSwingRuleLint.lint(
                            "ModifierNodeInspectableProperties",
                            sourceWithLibrarySwingModifierImport(it),
                            Config.empty,
                            """
                            package org.jetbrains.compose.swing.modifier

                            interface SwingModifier {
                                interface InspectableElement {
                                    val name: String get() = ""
                                    val declaredValues: Map<String, Any?> get() = emptyMap()
                                }
                            }
                            """.trimIndent(),
                        )
                    },
                    "internal data class KeyElement(val tokens: List<Any?>) : SwingModifier.InspectableElement",
                    "KeyElement : InspectableElement",
                ),
                case(
                    "SwingComponentInComposableSignature names the declaration of an inferred return type",
                    { ComposeSwingRuleLint.lint("SwingComponentInComposableSignature", it) },
                    "import androidx.compose.runtime.Composable\nimport javax.swing.JPanel\n\n" +
                        "@Composable\nfun parentOf() = JPanel().parent",
                    "@Composable fun parentOf",
                ),
            )
    }
}
