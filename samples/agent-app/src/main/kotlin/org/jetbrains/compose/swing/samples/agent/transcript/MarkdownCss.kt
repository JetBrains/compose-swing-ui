package org.jetbrains.compose.swing.samples.agent.transcript

import org.jetbrains.compose.swing.samples.agent.theme.AgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.mix
import java.awt.Color
import java.util.Locale

/**
 * The style sheet the rendered Markdown reads, built from [theme]'s tokens, for text that reads left to right when
 * [isLeftToRight] is `true`.
 *
 * Swing's HTML kit understands a CSS1 subset: no rounded corners, no `:first-child`, and list indents through its own
 * `margin-left-ltr` and `margin-right-rtl`, which follow the reading direction. Lengths are in `pt` because the kit
 * maps one point to one pixel and scales `px` by 1.3.
 */
internal fun markdownCss(
    theme: AgentTheme,
    isLeftToRight: Boolean,
): String {
    val colors = theme.colors
    val leading = if (isLeftToRight) "left" else "right"
    val rule = colors.border.mix(colors.mutedText, 0.35f)
    val body = theme.fonts.body
    val mono = theme.fonts.mono
    val size = body.size
    // Each heading level is one step larger than the next, from the body size at h4 upwards.
    val step = 2
    val h3 = size + step
    val h2 = h3 + step
    val h1 = h2 + step
    return """
        body { font-family: ${body.family}; font-size: ${size}pt; color: ${colors.text.toCssHex()}; margin: 0; }
        p { margin-top: 0; margin-bottom: 8pt; }
        h1, h2, h3, h4, h5, h6 { color: ${colors.text.toCssHex()}; margin-top: 4pt; margin-bottom: 8pt; }
        h1 { font-size: ${h1}pt; }
        h2 { font-size: ${h2}pt; }
        h3 { font-size: ${h3}pt; }
        h4, h5, h6 { font-size: ${size}pt; }
        a { color: ${colors.accent.toCssHex()}; text-decoration: none; }
        code { font-family: ${mono.family}; font-size: ${mono.size}pt; background-color: ${colors.codeBackground.toCssHex()}; }
        pre {
            font-family: ${mono.family}; font-size: ${mono.size}pt; background-color: ${colors.codeBackground.toCssHex()};
            padding: 8pt 12pt; margin-top: 0; margin-bottom: 8pt;
        }
        ul, ol { margin-top: 0; margin-bottom: 8pt; margin-left-ltr: 20pt; margin-right-rtl: 20pt; }
        ul li ul, ol li ol, ul li ol, ol li ul { margin-bottom: 0; margin-left-ltr: 16pt; margin-right-rtl: 16pt; }
        li { margin-bottom: 2pt; }
        li.task, li.done { list-style-type: none; }
        li.done { color: ${colors.mutedText.toCssHex()}; }
        blockquote {
            color: ${colors.mutedText.toCssHex()}; margin: 0 0 8pt 0; padding: 0 12pt;
            border-$leading: 3px solid ${rule.toCssHex()};
        }
        table { border-spacing: 0; margin-bottom: 8pt; }
        th, td { border-bottom: 1px solid ${colors.border.toCssHex()}; padding: 4pt 12pt 4pt 0; }
        th { background-color: ${colors.raisedSurface.toCssHex()}; font-weight: bold; text-align: $leading; }
        hr { color: ${colors.border.toCssHex()}; margin-bottom: 8pt; }
        .user-del { text-decoration: line-through; color: ${colors.mutedText.toCssHex()}; }
        """.trimIndent()
}

private fun Color.toCssHex(): String = String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue)
