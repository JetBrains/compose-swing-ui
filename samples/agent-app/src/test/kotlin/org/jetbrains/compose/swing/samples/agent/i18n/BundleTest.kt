package org.jetbrains.compose.swing.samples.agent.i18n

import java.text.MessageFormat
import java.util.Locale
import java.util.PropertyResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundleTest {
    @Test
    fun everyLanguageFileHasEveryKeyWithTheSamePlaceholders() {
        val english = ownEntries(AppLanguage.English)
        for (language in AppLanguage.entries) {
            val entries = ownEntries(language)
            assertEquals(english.keys, entries.keys, "$language has the same keys as English")
            for ((key, value) in entries) {
                assertTrue(value.isNotBlank(), "$language: $key is empty")
                assertEquals(
                    arguments(english.getValue(key)),
                    arguments(value),
                    "$language: $key formats the arguments English formats",
                )
            }
        }
    }

    @Test
    fun pluralsFollowTheCount() {
        assertEquals("1 token", message(AppLanguage.English.locale, "status.tokens", 1))
        assertEquals("1,200 tokens", message(AppLanguage.English.locale, "status.tokens", 1200))
        assertEquals("Keine Tokens", message(AppLanguage.German.locale, "status.tokens", 0))
    }

    @Test
    fun theStartupLanguageFollowsTheSystemLocaleAndFallsBackToEnglish() {
        assertEquals(AppLanguage.German, AppLanguage.closestTo(Locale.GERMANY))
        assertEquals(AppLanguage.Russian, AppLanguage.closestTo(Locale.forLanguageTag("ru-RU")))
        assertEquals(AppLanguage.English, AppLanguage.closestTo(Locale.US))
        assertEquals(AppLanguage.English, AppLanguage.closestTo(Locale.JAPANESE))
        assertEquals(listOf("English", "Deutsch", "Русский"), AppLanguage.entries.map { it.displayName })
    }

    /**
     * The entries [language]'s own file declares. A `ResourceBundle` would also list the keys it inherits from the
     * base file, so a key missing from a translation would go unnoticed.
     */
    private fun ownEntries(language: AppLanguage): Map<String, String> {
        val suffix = if (language == AppLanguage.English) "" else "_${language.locale.language}"
        val path = "/${BUNDLE.replace('.', '/')}$suffix.properties"
        val bundle =
            checkNotNull(javaClass.getResourceAsStream(path)) { "No $path" }.use { PropertyResourceBundle(it) }
        return bundle.keySet().associateWith { bundle.getString(it) }
    }

    /** How many arguments [pattern] formats: one more than the highest index it names. */
    private fun arguments(pattern: String): Int = MessageFormat(pattern).formatsByArgumentIndex.size
}
