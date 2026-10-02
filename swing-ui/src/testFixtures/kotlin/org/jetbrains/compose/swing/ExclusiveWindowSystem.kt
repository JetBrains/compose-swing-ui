package org.jetbrains.compose.swing

import org.junit.jupiter.api.Tag

/**
 * Declares that a test's outcome depends on which of this process's windows the window system is
 * attending to. That covers asserting on focus or minimization, reading screen pixels, and holding a
 * popup open that another window taking the focus would close.
 *
 * That is a single, machine-wide state, so two such tests cannot run at the same time - one asking to be
 * focused takes the focus away from the other, which then reports a withheld capability and skips. A skip
 * is silent lost coverage rather than a failure, so the separation is a correctness measure, not a
 * performance one.
 *
 * Tests carrying this tag run in a test task of their own that runs them one at a time. Everything else
 * runs across parallel JVMs: those tests realize and show real windows too, and the focus that moves
 * between them is nothing they assert on.
 *
 * The tag goes on a class when every case in it depends on that state, and on a single case when only
 * that case does.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag(EXCLUSIVE_WINDOW_SYSTEM_TAG)
public annotation class ExclusiveWindowSystem

/**
 * The name the build's test-task split filters on: one task excludes it, the other includes it. The
 * build declares the same literal, which is why it reads as a name rather than a detail of this file.
 */
public const val EXCLUSIVE_WINDOW_SYSTEM_TAG: String = "exclusive-window-system"
