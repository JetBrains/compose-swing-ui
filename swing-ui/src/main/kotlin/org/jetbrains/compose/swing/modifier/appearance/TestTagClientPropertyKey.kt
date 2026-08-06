package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi

/**
 * The `JComponent` client-property key under which [testTag] stores its tag. The test harness reads
 * this key to resolve a tagged component; it is not intended for application use.
 *
 * Kept out of the `AppearanceModifierKt` multifile facade: the ABI-validation `annotatedWith` exclusion
 * for [InternalSwingUiApi] reads a property's annotation off the synthetic method the Kotlin compiler
 * generates for it, and a facade never forwards that synthetic method - only a class that carries the
 * property directly keeps its `internal` marker visible to the gate.
 */
@InternalSwingUiApi
public val TEST_TAG_CLIENT_PROPERTY_KEY: Any = "org.jetbrains.compose.swing.testTag"
