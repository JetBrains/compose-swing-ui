# Module swing-ui-svg

Draws SVG in a Compose Swing UI composition, backed by [JSVG](https://github.com/weisJ/jsvg). A document's shapes are drawn antialiased by default, and the drawing stays sharp on a scaled display.

## Usage

`SvgImage` puts a document in the tree as an element of its own. Its preferred size is the document's intrinsic size plus its border insets, which the preferred-size style setter overrides. The document is drawn inside the border.

```kotlin
SvgImage(document = logo, style = { preferredSize(Dimension(48, 48)) })
```

An ancestor can use `provideNestedStyle(SvgImages)` to set defaults for its descendant SVG images. The image's own `style` takes precedence per property. Import `SvgImages` from `org.jetbrains.compose.swing.svg.style`.

`rememberSvgIcon` turns a document into a `javax.swing.Icon`. Use it wherever Swing shows an icon rather than hosts a component, such as the icon style setters of `swing-ui-components` or a tab header. The icon also stays sharp on a scaled display.

```kotlin
val saveIcon = rememberSvgIcon(saveGlyph)
Button(text = "Save", onClick = ::save, style = { icon(saveIcon) })
```

## Loading

Pass a parsed JSVG document to `SvgImage` or `rememberSvgIcon`. You can reuse the same document at several sizes.

`rememberSvgDocument` loads off the event dispatch thread and returns `null` until the first document loads. Changing the URL, loader context, or parse context reloads the document and keeps the previous result until the replacement loads. A `null` loader context uses JSVG's defaults. Keep custom contexts stable across recompositions. Load fallible sources outside the composition if you need to recover from a load error. Errors from `rememberSvgDocument` go to the composition's exception handler.

## Animation

A document with `<animate>` tracks plays while it is composed, paced by the frame clock of the window it is in. A track that repeats indefinitely plays until the document leaves the tree. A timeline that ends stops at its end, where a track with `fill="freeze"` keeps its final value. In a test, drive the frame clock by hand to watch it play.

Pass an `SvgAnimationState` to observe or control playback. Both the constructor and
`rememberSvgAnimationState` accept `initialTime` and `initiallyPlaying`, which default to
`Duration.ZERO` and `true`. These values apply when the state is created. To control an
existing state, write to its properties.

Setting `isPlaying` to `false` pauses on the current frame, and setting it back to `true` resumes from there. Writing `time` seeks. While `isPlaying` is `true`, a seek to before the end of a finished timeline resumes playback. Omitting `animation` or passing `null` gives each animated document its own playback state, so a replacement plays from its start. A state passed in keeps its position across documents. Several `SvgImage`s can share a state and show the same position. When their timelines end at different times, the position runs on to the longest end. Images that share a state are expected to be in one window, at one motion scale. With motion off, an image of a finite timeline finishes at once, which moves the shared position to its end.

JSVG animates `<animateTransform>` and numeric attributes such as `fill-opacity`, from `values` or from `from`/`to`. Path morphing through `d` is unsupported. Keyframes are spaced evenly and interpolated linearly whatever `calcMode`, `keySplines` and `keyTimes` specify, so write `time` yourself to apply a designed easing curve.

When a document's tracks differ in length, a shorter track with `fill="freeze"` that ends after a whole number of repeats shows its first frame for the single instant at which `time` equals its own end, and its final value after that.

Playback honors the `MotionDurationScale` on the context the composition's effects run in, so an application that offers a reduce-motion preference slows or stops SVG animation with everything else. Set it process-wide through `compose.swing.motionDurationScale`, or for one composition through the context its recomposer is built over.
