package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.compose.swing.core.MotionDurationScale

/**
 * The app's "Reduce motion" switch, read from [settings]. It sits on the application's coroutine context, so every
 * animation in every window reads it when it starts: while it is enabled, animations jump to their end.
 */
internal class ReduceMotion(
    private val settings: SettingsState,
) : MotionDurationScale {
    val isEnabled: Boolean get() = settings.settings.isReduceMotion

    override val scaleFactor: Float get() = if (isEnabled) 0f else 1f
}
