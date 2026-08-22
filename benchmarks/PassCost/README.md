# PassCost

What one composition pass costs the runtime, in bytes allocated and in time held on the thread the
composition runs on.

```bash
./gradlew :benchmarks:PassCost:run
```

Nothing here is a test and nothing here is shown. The module stands the composition runtime up
directly - a `BroadcastFrameClock`, a `Recomposer` on the event dispatch thread, content mounted with
`setContent(parent = recomposer)` on a bare `JPanel` - and drives passes by publishing the pass's state
writes, sending a frame, and draining the event queue. No widget is ever realized, so no pass pays for a
layout or a paint, and no run holds the window-system lock.

## The null gate

Every arm is measured twice. Once driving the change it names, and once - its null variant - driving the
identical tree through the identical protocol with nothing changed at all. The report prints the null
beside every arm and states each arm net of its own null.

That is the whole point of the module. A benchmark whose null case is not near zero is measuring its own
driver, and no figure it prints can be attributed to the change it names. The runner prints, loudly, where
a null arm exceeded its budget, and ends the run with a non-zero status when one did.

## Reading the table

Allocation is reproducible: the same arm on the same tree gives the same bytes per pass in every batch,
to a fraction of a percent, and usually the same figure again in the next run. A run's own
just-in-time decisions can still shift one arm by ten percent or more while every other arm holds, so
two arms are compared inside one run and never across two. Wall-clock is not reproducible - it moves by up to 2.2x with machine load - so the time
columns are printed per batch and read as a spread, never as a point estimate.

Batches are printed one by one rather than averaged, so the process's own warm-up, which an arm's
discarded passes cannot reach, shows in the first arm measured instead of hiding in a mean.

The frames column is how many frames a pass took to settle. One is a pass that settled on the first
frame. Two is a pass whose applied changes wrote state the pass itself read, so the scope that read it
was invalidated by its own apply: the change is on the widget after the first frame, and the second is
the composition settling with itself. Both frames are inside the bytes and the time beside them, so an
arm that reads two frames is paying for a pass the caller never asked for.

## The arms

Each is measured at a small size and a large one, on the axis its cost is linear in: the widget arms on a
column of one widget and a column of 200, the tree and table arms on 1 and 200 child nodes or rows.

| arm | what changes |
| --- | --- |
| `one property changed` | a label reads the changing text itself, so its scope alone re-executes |
| `read one scope above` | the read sits in the column's content scope, so every call in it re-executes |
| `structural insert` / `structural remove` | one widget appears and disappears at the end of the tree |
| `modifier chain unchanged` | four modifier elements re-declared identically, so the chain is skipped |
| `modifier chain rebuilt` | the same chain, with a listener callback of a new identity each pass |
| `node key only` | an invalidated node whose update block holds one key |
| `value keys only` | the same node, plus 16 two-part keys of the shape a declaration is keyed on |
| `declared two-way` | the same node, plus 16 two-way declarations settled on what the widget holds |
| `declared two-way moved` | the same 16 declarations, moved onto a value the widget does not hold |
| `slider value changed` | a ticked, labeled slider whose declared value moves every pass |
| `tree value changed` | one node's label differs, in a tree whose whole structure is declared as data |
| `tree node added` / `tree node removed` | one node appears and disappears at the end of that same tree |
| `table row changed` | one row's cell value differs, in a table whose rows are declared as data |
| `table row appended` / `table row removed` | one row appears and disappears at the end of that same table |
| `list items changed` | one item's text differs, in a list box whose items are declared as data |
| `list selection changed` | the declared selection moves to another row, over items that never change |
| `tree selection changed` | the declared selection moves to another node, over a structure that never changes |
| `table selection single` | the declared selection moves to another row, in a mode holding one row |
| `table selection interval` | the same, on a run of six rows, in a mode holding one interval |
| `table selection multiple` | the same, on rows five apart across the table, in a mode holding several |

`node key only`, `value keys only` and `declared two-way` are read against each other: what a key costs,
and what a whole declaration costs over it.

`declared two-way` and `declared two-way moved` are read against each other for what a declaration costs
when the widget has to be written rather than left alone. Both settle in one frame: a settle publishes the
widget's new value back into the mirror the declaring scope read, but a move a settle made and recorded
the answer to is not news to that scope, so no second frame is asked for. `slider value changed` is a real
component of that shape, and settles in one frame for the same reason.

The two tree arms are read against each other as well. A tree that changed a value is told apart from the
one before it only after every node has been compared; a tree that changed size is told apart at the
first child count. Both then rebuild the whole node tree, which is what their bytes are. A comparison
allocates nothing, so it can show in the time columns alone.

The table arms carry the module's own caveat: nothing here is realized, so what a table does in answer to
a change event is never paid for. What their bytes hold is the composition pass - copying the rows the
composition declares and comparing them against the rows the model holds.

The selection arms are read against the value arms of the same widget. A selection change touches no item
and no row, so what separates the two is the content work a pass does anyway: a list box or a table copies
the items or rows it is declared with and compares them against what its model holds, whatever else moved.
Each selection is a set the arm's mode holds whole - a mode that narrowed the set it was given would leave
the widget somewhere the declaration never asked for, and every later pass would write the same
declaration again. A widget of one item or row has no second row to move to, so there every mode
alternates between that row and no selection at all, and the three table modes measure the same thing.

The table selection arms count how many times the table itself re-declared its rows and columns, alongside
how many times the arm's own content scope ran. Where such an arm takes a second frame, that count is what
tells a frame that re-ran the node's update block over what the first left behind from one that composed
the table again.
