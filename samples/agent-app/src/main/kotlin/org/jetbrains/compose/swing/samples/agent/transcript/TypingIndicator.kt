package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.samples.agent.components.Card
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import kotlin.math.PI
import kotlin.math.sin

/** Three dots rising in turn, in a raised card, while the agent works between visible outputs; composed only then. */
@Composable
internal fun ConstrainedScope.TypingIndicator(modifier: SwingModifier = SwingModifier) {
    val theme = LocalAgentTheme.current
    val colors = theme.colors
    val phase by rememberInfiniteTransition(label = "typing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "typing phase",
    )
    Card(modifier.accessibleName(message("transcript.typing")), StyleClass.RaisedCard) {
        Canvas(SwingModifier.size(30, 10)) {
            for (dot in 0..2) {
                // Each dot lifts during its own slice of the cycle, a fifth of a cycle after the one before.
                val local = (phase - dot * 0.2f).mod(1f)
                val lift = if (local < 0.4f) sin(PI.toFloat() * local / 0.4f) else 0f
                drawCircle(
                    colors.mutedText,
                    radius = 3f,
                    centerX = 4f + dot * 11f,
                    centerY = 6f - 2f * lift,
                    alpha = 0.35f + 0.65f * lift,
                )
            }
        }
    }
}
