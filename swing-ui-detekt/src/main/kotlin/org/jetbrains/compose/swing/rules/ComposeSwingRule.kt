// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * A rule about Kotlin source, independent of the tool that runs it.
 *
 * A host may call the visitor concurrently, in any order, and for only the elements in a region.
 * Each callback checks the supplied element and its PSI context independently. The host drives
 * traversal. Each report's range must lie within the element supplied to that callback.
 */
public interface ComposeSwingRule {
    /** The name the rule is configured and suppressed by. Unique across every rule set. */
    public val id: String

    /** One sentence saying what the rule reports. */
    public val description: String

    /** How seriously a host treats a report. */
    public val severity: Severity

    /** Whether a host runs the rule when its configuration does not mention it. */
    public val activeByDefault: Boolean

    /** Whether the rule resolves symbols or types, and so needs a host that can. */
    public val requiresAnalysisApi: Boolean

    /**
     * Creates a visitor that sends findings to [reporter] using the settings in [config].
     * Keep mutable visit state local to each callback. The host supplies a configuration and a
     * reporter that support concurrent calls.
     */
    public fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid

    /** How seriously a host treats a report. */
    public enum class Severity {
        /** A problem to fix. */
        Error,

        /** A problem worth a look. */
        Warning,

        /** A remark. */
        Info,
    }
}

/** Receives the problems a rule finds. */
public fun interface ComposeSwingRuleReporter {
    /** Reports a problem at [element], described by [message]. */
    public fun report(
        element: PsiElement,
        message: String,
    )
}

/** The settings a host holds for one rule. */
public interface ComposeSwingRuleConfig {
    /**
     * Returns the configured value under [key] when [default] is non-null and a value is configured.
     * Returns [default] when nothing is configured under [key], or when [default] is null.
     * The types a host must supply for T are Int, Boolean and String.
     */
    public fun <T> valueOrDefault(
        key: String,
        default: T,
    ): T

    /**
     * Returns the list configured under [key], or [default] when nothing is configured.
     * A host also reads a single string of names separated by commas or semicolons as a list.
     */
    public fun valueOrDefault(
        key: String,
        default: List<String>,
    ): List<String>
}
