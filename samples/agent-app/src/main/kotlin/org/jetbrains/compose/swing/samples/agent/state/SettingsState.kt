package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.swing.samples.agent.data.SettingsRecord
import org.jetbrains.compose.swing.samples.agent.data.SettingsRepository
import org.jetbrains.compose.swing.samples.agent.harness.DEFAULT_PI_EXECUTABLE
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode

/** The user's appearance, language and agent choices. */
@Immutable
internal data class Settings(
    val themeMode: ThemeMode = ThemeMode.Dark,
    val language: AppLanguage = AppLanguage.English,
    /** Mirrors the whole window right to left, in any language, to check the layout's mirroring. */
    val isRightToLeft: Boolean = false,
    val isReduceMotion: Boolean = false,
    /** The path to the pi executable. */
    val piExecutable: String = DEFAULT_PI_EXECUTABLE,
)

/**
 * Holds the current [Settings] and stores every change in [repository]. Changing them has no other effect: the
 * window installs the look and feel and the JVM's default locale they name.
 */
@Stable
internal class SettingsState(
    initial: Settings,
    private val repository: SettingsRepository,
    scope: CoroutineScope,
) {
    private val writes = WriteQueue(scope)

    var settings by mutableStateOf(initial)
        private set

    /** The first exception a write of this state threw, or `null` while every change has been stored. */
    val writeFailure: Throwable? get() = writes.failure

    /** Returns once every change so far is stored or has failed, with the [writeFailure], if any. */
    suspend fun awaitWrites() = writes.awaitWrites()

    private fun update(change: Settings.() -> Settings) {
        settings = settings.change()
        val record = settings.toRecord()
        writes.writeLatest(SettingsRecord::class) { repository.save(record) }
    }

    fun selectTheme(mode: ThemeMode) = update { copy(themeMode = mode) }

    fun toggleTheme() = selectTheme(if (settings.themeMode == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark)

    fun selectLanguage(language: AppLanguage) = update { copy(language = language) }

    fun setRightToLeft(enabled: Boolean) = update { copy(isRightToLeft = enabled) }

    fun setReduceMotion(enabled: Boolean) = update { copy(isReduceMotion = enabled) }

    /** A blank path restores the default executable. */
    fun setPiExecutable(path: String) = update { copy(piExecutable = path.trim().ifEmpty { DEFAULT_PI_EXECUTABLE }) }

    companion object {
        /** The stored settings, or [default] when none are stored yet. */
        suspend fun stored(
            repository: SettingsRepository,
            default: Settings = Settings(),
        ) = repository.settings()?.toSettings(default) ?: default
    }
}

private fun Settings.toRecord() =
    SettingsRecord(
        themeMode = themeMode.name,
        language = language.name,
        isRightToLeft = isRightToLeft,
        isReduceMotion = isReduceMotion,
        piExecutable = piExecutable,
    )

// A stored name this build does not know, from a newer or older build, keeps the default for that setting.
private fun SettingsRecord.toSettings(default: Settings) =
    Settings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: default.themeMode,
        language = AppLanguage.entries.firstOrNull { it.name == language } ?: default.language,
        isRightToLeft = isRightToLeft,
        isReduceMotion = isReduceMotion,
        piExecutable = piExecutable,
    )
