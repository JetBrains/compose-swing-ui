package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

// The extensibility lesson: the same primitive that builds the library's wrappers - SwingNode - folds
// your own Swing component into the composition as a first-class citizen. factory constructs it, update
// pushes state onto it, and a mouseListener modifier reports input back out; from the composition's
// point of view it behaves exactly like a built-in widget.
@Composable
internal fun CustomComponentSection() {
    SectionColumn {
        SectionHeading("Custom component")
        ExampleCard("A hand-written JComponent wrapped with SwingNode") {
            WrappedCaption(
                "StarRating is a plain Swing JComponent defined right in this sample - not a library " +
                    "wrapper. SwingNode folds it into the composition: state flows in through `update`, " +
                    "clicks flow out through a mouseListener, and it drives the composables around it.",
            )

            var rating by remember { mutableIntStateOf(3) }

            FlowPanel(alignment = FlowLayout.LEADING) {
                StarRating(
                    rating = rating,
                    onRatingChange = { rating = it },
                    modifier = SwingModifier.testTag(STAR_RATING_TAG),
                )
                Label("$rating / $MAX_STARS")
            }

            Button(
                text = "Clear",
                onClick = { rating = 0 },
            )
        }
    }
}

@Composable
private fun StarRating(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    // rememberUpdatedState keeps the latest callback without re-attaching the listener every recomposition.
    val onChange = rememberUpdatedState(onRatingChange)
    val listener =
        remember {
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    val star = (event.x / (event.component.height)) + 1
                    onChange.value(star.coerceIn(1, MAX_STARS))
                }
            }
        }
    SwingNode(
        factory = { StarRatingComponent() },
        update = {
            set(rating) { this.rating = it }
            applyModifier(
                modifier
                    .mouseListener(listener)
                    .preferredSize(Dimension(MAX_STARS * STAR_BOX, STAR_BOX)),
            )
        },
    )
}

private const val MAX_STARS = 5
private const val STAR_BOX = 32

internal const val STAR_RATING_TAG: String = "custom-star-rating"

private class StarRatingComponent : JComponent() {
    var rating: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, MAX_STARS)
            if (field != clamped) {
                field = clamped
                repaint()
            }
        }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val box = height
            repeat(MAX_STARS) { index ->
                g.color = if (index < rating) FILLED else EMPTY
                g.fill(starShape(index * box, box))
            }
        } finally {
            g.dispose()
        }
    }

    private fun starShape(
        x: Int,
        box: Int,
    ): Polygon {
        val cx = x + box / 2.0
        val cy = box / 2.0
        val outer = box * OUTER_RADIUS_FRACTION
        val inner = box * INNER_RADIUS_FRACTION
        val star = Polygon()
        repeat(STAR_POINTS * 2) { step ->
            val radius = if (step % 2 == 0) outer else inner
            val angle = Math.PI / STAR_POINTS * step - Math.PI / 2
            star.addPoint(
                (cx + radius * Math.cos(angle)).toInt(),
                (cy + radius * Math.sin(angle)).toInt(),
            )
        }
        return star
    }

    private companion object {
        val FILLED = Color(0xF5, 0xA6, 0x23)
        val EMPTY = Color(0xCF, 0xCF, 0xCF)
        const val STAR_POINTS = 5
        const val OUTER_RADIUS_FRACTION = 0.45
        const val INNER_RADIUS_FRACTION = 0.18
    }
}
