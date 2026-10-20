package org.jetbrains.compose.swing.samples.agent.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle
import javax.swing.JComponent
import javax.swing.UIManager

@NonNls
internal const val BUNDLE = "messages.AgentBundle"

/** The languages the app ships translations for. */
internal enum class AppLanguage(
    val locale: Locale,
) {
    English(Locale.ENGLISH),
    German(Locale.GERMAN),
    Russian(Locale.forLanguageTag("ru")),
    ;

    /** The name the language calls itself, so a user finds theirs in any UI language. */
    val displayName: @Nls String get() = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }

    companion object {
        /** The language of [locale], or English when the app ships no translation for it. */
        fun closestTo(locale: Locale): AppLanguage =
            entries.firstOrNull { it.locale.language == locale.language } ?: English
    }
}

/**
 * The locale every [message] in the composition is formatted for. A static local invalidates everything that reads
 * it, so every string is read again on a switch.
 */
@Suppress("CompositionLocalAllowlist") // An app-wide setting every label reads, provided once at the root.
internal val LocalAppLocale = staticCompositionLocalOf { AppLanguage.English.locale }

/** The bundle text for [key] in the composition's locale, with [args] formatted into it. */
@Composable
internal fun message(
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg args: Any,
): @Nls String = message(LocalAppLocale.current, key, *args)

/** The bundle text for [key] in [locale], with [args] formatted into it; plurals are `choice` patterns. */
internal fun message(
    locale: Locale,
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg args: Any,
): @Nls String = MessageFormat(bundle(locale).getString(key), locale).format(args)

// Without this control a missing translation falls back to the JVM default locale, not the base bundle.
private val noFallback = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)

internal fun bundle(locale: Locale): ResourceBundle = ResourceBundle.getBundle(BUNDLE, locale, noFallback)

/** Makes [locale] the default for Swing's own texts too: file choosers, option panes, input maps. */
internal fun applyDefaultLocale(locale: Locale) {
    Locale.setDefault(locale)
    JComponent.setDefaultLocale(locale)
    UIManager.getDefaults().defaultLocale = locale
}
