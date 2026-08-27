package org.jetbrains.compose.swing.platform

import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi

/**
 * An operating system family whose Swing behavior differs from the others'.
 *
 * [Unknown] covers every system outside the three families; code branching on the host treats it as
 * the default case.
 */
@InternalSwingUiApi
public enum class HostOs {
    /** Any Linux distribution. */
    Linux,

    /** Any version of Windows. */
    Windows,

    /** macOS. */
    MacOs,

    /** Every system outside the three families, and a JVM that names none. */
    Unknown,
    ;

    /** Whether this is [Linux]. */
    public val isLinux: Boolean get() = this == Linux

    /** Whether this is [Windows]. */
    public val isWindows: Boolean get() = this == Windows

    /** Whether this is [MacOs]. */
    public val isMacOs: Boolean get() = this == MacOs
}

/**
 * The family the given `os.name` value names, matched on the name's leading word regardless of case.
 *
 * Resolves to [HostOs.Unknown] for a name outside the three families, and for an empty name.
 */
@VisibleForTesting
internal fun hostOsOf(osName: String): HostOs =
    when {
        osName.startsWith("Mac", ignoreCase = true) -> HostOs.MacOs
        osName.startsWith("Windows", ignoreCase = true) -> HostOs.Windows
        osName.startsWith("Linux", ignoreCase = true) -> HostOs.Linux
        else -> HostOs.Unknown
    }

/**
 * The operating system this JVM runs on, resolved once from the `os.name` system property and kept for
 * the JVM's lifetime.
 */
@InternalSwingUiApi
public val hostOs: HostOs by lazy { hostOsOf(System.getProperty("os.name").orEmpty()) }
