# SwingMark

The JDK's own Swing benchmark, run twice over in one VM: once on raw Swing widgets, once on the same
screens declared through compose-swing-ui.

## Running the two

```bash
./gradlew :benchmarks:SwingMark:run --args="-q -r 5"
```

That is the measurement. Both arms are here, both are timed by the same harness, and the suite closes with
what they cost against each other: each arm's median over the warm runs, that arm's floor, what is left of
it, the ratio between the two remainders, a bootstrapped 95% interval for that ratio, and each arm's paint
count. An interval covering 1.0 reads `not separated`, which is the honest answer when the runs do not
tell the two arms apart.

Three conditions, and a run that misses any of them reports something other than what it claims.

**Enough runs to say anything.** Run 1 of a VM is its warm-up and is dropped, and an interval wants three
warm runs behind it, so `-r 4` is the shortest run that carries one. A shorter run prints the medians and
says `too few runs` where a ratio would be.

**Both arms meeting the same machine load.** A wall-clock timing on a loaded machine moves by a factor of
two, which is larger than anything being measured - but a *ratio* between two arms interleaved under that
load does not. The two arms of a test run back to back, and which of them goes first alternates, so a
machine that drifts over a run drifts across both. Stop the Gradle daemon before a run that matters.

**The window on screen, uncovered, and holding the keyboard focus.** A run behind a hidden or covered
window paints a fraction of the work. `Sub-Menus` posts key events, which reach a menu only while the
window holds the focus.

## The command line

The original's, option for option.

| option | effect |
| --- | --- |
| `-q` | exit when the last run finishes |
| `-r <n>` | run the suite `n` times in the same VM |
| `-lf <class>` | use this look and feel |
| `-n` | use the platform's look and feel; refuses to combine with `-lf` |
| `-f <file>` | write the timing report as XML, a row per arm of each test |
| `-m <file>` | write the memory report as XML |
| `-db=off` | run without double buffering |
| `-sleep` | beep and collect between runs |
| `-blit` | scroll by blitting |
| `-version` | print what this suite is |

Without `-lf` or `-n` the suite runs under Metal, which is the original's default and the only way the
arms are comparable to the original: a look and feel decides how much painting each change costs.

An unrecognised option ends the run, as it does in the original.

## The tests

The six the original's `TestList.txt` names. Each is a pair of classes named after the original's class,
one per arm, and each reports under the name the original reports.

| original | reports as |
| --- | --- |
| `JMTest_04` | `Sub-Menus` |
| `TextAreaTest` | `TextArea` |
| `SliderTest` | `Sliders` |
| `ListTest` | `Lists` |
| `TableRowTest` | `Table Rows` |
| `TreeTest` | `Tree` |

The remaining seventeen join the suite the same way: one file per arm, and one line in the suite's list of
tests.

## The two arms

**Raw.** The original's Kotlin translation - the same widgets in the same containers, holding the same
data, changed by the same setters in the same order and the same number of times. Where the original posts
a change and waits for the queue, so does this; where it waits for the change itself, so does this.

**Declared.** The same screen written through the library, changed by writing state. Where the original
calls a setter, this writes state and waits for the widget to carry it; where the original scrolls, this
scrolls through the state holder the library offers for it - `ListState.revealIndex`,
`TreeState.revealPath`, `ScrollState.revealRect`.

Everything else is one harness the two share: the order the tests run in, the wait between changes, the
frame clock, the paint reset, the report.

## The floor under each arm

The two arms do not wait on the same things the same number of times. A raw change is a setter handed to
the event dispatch thread; a declared change is a state write published, a frame delivered, and the widget
read once the queue falls idle. Waiting costs something on its own, so a ratio taken over the two times as
they stand is partly a ratio between the suite's own waits.

So every arm is timed twice. Once driving its changes, and once - its floor - driving the same waits in
the same numbers with nothing behind them: no state written, no setter called, on the screen the run left
behind. Nothing behind a step leaves a composition nothing to recompose and a widget nothing to repaint,
so what the floor costs is the suite waiting on itself. It is printed beside the arm, taken off it, and
the net ratio is between what is left of the two. The plain ratio beside it is over the times as they
stand, for a reading of the table against one taken before the floor was measured.

The floor also carries the wait, the collection and the drain the original makes at the end of every timed
test, which both arms pay and neither causes. That is a constant of well over a hundred milliseconds -
most of a short test and little of a long one - and it is why the two arms of a short test read closer
together than they are.

Where an arm's floor covers almost all of its time, the suite says so under the table: what is left is its
own spread rather than a measurement, and the ratio beside it means nothing.

## What the harness does

- **Frames are sent, not awaited.** The harness mounts the declared arm on a composition runtime of its
  own and delivers each frame itself, at a point it chooses. The raw arm has no clock to drive - a setter
  is applied where it is called.
- **Every declared change costs a settle.** A change publishes the state write and sends a frame before it
  asks whether the widgets carry the change. A predicate asked first would return on a change that had not
  run yet, leaving the invalidation to batch into a later one and charging that one for both.
- **A change waits once.** The widget is read on the event dispatch thread by the probe that finds the
  queue empty, so the settle is the only wait a change makes and no round trip is spent on the question.
  The probe answers that question and does nothing else: work started from there would land in a turn of
  its own.
- **A scroll rides the change that asks for it.** The raw arm mutates its widget and scrolls it in one
  runnable, so both dirty regions reach the repaint manager in one flush; the declared arm asks as it
  writes the state, and the scroll is carried out in the turn that applies the change. An arm scrolling
  a turn later would be charged for a paint its counterpart never makes.
- **Waiting is the original's.** The wait between changes is SwingMark's own, ported as it stands.
- **Nothing waits forever.** A wait gives up after five seconds, a declared change after two, and a
  watchdog halts a run that has made no progress for fifteen and prints where the event dispatch thread
  was.

## Reading the paint columns

The two arms count paints differently, and the columns are not comparable to each other.

The raw arm counts as the original counts, by subclassing the widget under test, so its figures stand
beside the original's. The declared arm builds no widget of its own, so its paints are counted at the
repaint manager: one per flush of the dirty regions rather than one per widget painted. The two agree
closely on a screen holding one widget, and the declared figure reads far lower on a screen that scrolls,
because a viewport paints itself as it scrolls rather than marking a region dirty.

Read each column against its own arm's runs. A column that moves between runs of the same arm means that
arm stopped driving the work it drove before.

## Where the declared arm differs

**`Sub-Menus` opens no menu on macOS.** Its walk posts key events, and AWT delivers those only where the
platform lets it. Where it does not - macOS is one such place, for the original and both arms alike - the
test times the event queue and nothing else. The original reports that number in silence; both arms here
say so on the error stream first. Both arms post the same event, carrying the modifier mask AWT takes
today rather than the one the original was written against.

**The menu bar goes through the escape hatch.** The library declares a menu bar on a window, through
`WindowScope.MenuBar`, and `JMTest_04` puts its bar inside its own panel instead. So the declared arm
declares the bar as a `SwingNode` holding a `JMenuBar` in the panel's north region, and fills it with
`JMenuBar.setContent`. Its menu tree joins the pumped composition with the rest of the screen. The tree
never changes, so it never asks for a frame.

**`Lists` walks one row fewer.** The original's loop starts at the list's selected index, which is `-1`, so
its last pass selects a row the list does not have. The raw arm makes that pass, as its original does; the
declared arm walks the rows that exist.

**`Table Rows` declares a selection rather than adding to one.** The original adds an interval to whatever
the table already holds, so what a pass selects depends on every pass before it, and under two of the three
modes the selection saturates: once it covers what a later pass would add, that pass selects what is
already selected. Rather than restate which passes those are, the declared arm replays the original's own
calls against a selection model of the mode under test and declares the set each pass leaves - so the same
passes are no-ops there as here.

**`Tree` repeats what the original repeats.** Its expansion is declared, and opening the ancestors of a
node whose elder sibling is already open declares nothing new; so does closing a leaf. Those passes are the
original's own `scrollPathToVisible` and `collapsePath` calls on a node already in that state. They are
timed for the settle and the scroll they cost, not for a structural change they do not make.

**`Tree` drops a selection it closes over.** The tree settles expansion first and selection second, and a
`JTree` opens the parents of the selection it is given, so a node still holding a selected descendant
opens again the moment it is closed. The collapse phase therefore drops those paths and selects the
closing node in their place - which is what a `JTree` does for itself when the collapse is a call rather
than a declaration.

## Checking the raw arm against the JDK's own suite

`ab/run.sh` runs the JDK's SwingMark and this suite against each other and reports the raw arm beside the
original, per test. That is what proves the translation faithful: the raw arm drives the original's
widgets through the original's changes, so the two must paint the same number of times.

```bash
./gradlew :benchmarks:SwingMark:installDist
JBR=~/Projects/JetBrainsRuntime ab/run.sh
```

It builds the original from a copy of that checkout's `test/jdk/performance/client/SwingMark`, never
writing to the checkout itself, then alternates the two VM by VM and prints each test's median, the ratio,
a bootstrapped interval for it, and both paint counts. `JAVA=<path>` picks the runtime; both are launched
from it, because two runs on different runtimes compare the runtimes.

**The paint counts carry the check.** The original counts from the moment its window opens and this suite
from the start of each timed run, so the original reads one or two higher. Anything beyond that is work
one of them does and the other does not, and it is a defect in the translation until it is explained.

**The times are the weaker half.** This suite runs its declared arm in the same VM, so its raw arm meets a
warmer JIT and a busier heap than the original does. Read a separated interval as a reason to look. Read
the `drift` column too, which is the original's own first round against its last; a large one-directional
move means the run is reading the machine.
