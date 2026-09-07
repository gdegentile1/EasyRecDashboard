# EasyRec Dashboard (Swing)

A Java/Swing port of the Django `easyrec_dashboard` application, meant to live inside the
EasyRec GUI rather than beside it. Same tables, same arithmetic, same navigation.

## Integrating it

```java
// on the EDT, wherever EasyRec builds its tabs
tabbedPane.addTab("Dashboard", DashboardLauncher.createPanel(dataSource));
```

`dataSource` is a `javax.sql.DataSource` pointing at the database EasyRec already writes
`ER_DASHBOARD_*` to. Nothing else is required. The module installs no look and feel and
reads every colour through `UIManager`, so it inherits the host's FlatLaf theme and follows
a theme switch without a rebuild.

For a detached window during development: `DashboardLauncher.openWindow(dataSource)`.

## What maps to what

| Django route | Swing screen |
|---|---|
| `home` | `HomeView` — period buttons, headline KPIs, recent batches |
| `batch_list` | `BatchListView` — date range, multi-select feeding the comparison |
| `batch_detail` | `BatchDetailView` — KPIs, an editable description, and the batch's reconciliations |
| `run_template_detail` + `pivot_full` | `ReconciliationView` — context, KPI tiles, column stats, and the existing `ExcelTreeTable` pivot component |
| `batch_compare` / `template_compare` | `CompareView` — one screen, template level then column level |
| `batch_history` / `template_history` | `HistoryView` — two trend charts and the executions behind them |
| `api_chart_data` | gone; the charts read the same in-process objects the tables do |
| `user_settings` (theme) | gone; the host's look and feel decides |
| Django auth / admin | gone; EasyRec already knows who the user is |

## Layout

```
model/     records for the seven ER_DASHBOARD_* tables, plus StatusScope
dao/       DashboardDao + JdbcDashboardDao (portable SQL, bulk-by-id only)
service/   Rates, ContextValues, TemplateIds, PivotTreeBuilder,
           DashboardService, CompareService, HistoryService   (no Swing here)
ui/        the six screens, the navigator, the background-task helper
ui/component/  Palette, Fonts, Sections, DashboardIcons, DashboardTable,
               KpiCard, TrendChart, Renderers, StatusBadge, SegmentedControl,
               Tables
ui/pivot/      the adapter onto EasyRec's own pivot component
ui/table/      DashboardTableModel and the five models over it
```

The `service` layer is deliberately free of Swing. That is what made it testable, and the
arithmetic in it is the part that has to agree across four screens.

## The pivot breakdown reuses EasyRec's own component

`DashboardPivotPanel` is `PanelPivotActionBar` over `ExcelTreeTable` inside a `JViewer`,
wired the way `PanelPivot` wires them, so a breakdown read on the dashboard behaves like one
read on a live reconciliation: same level buttons, same expand and collapse, same search
box, same `views/break_statistics_*.xml` files.

What differs is the source of the numbers. A live pivot is recomputed from the appender;
this one was computed when the run happened and read back from `ER_DASHBOARD_PIVOT`. So
`DashboardPivotNodes` rebuilds the `ExcelNode` tree from those rows, after
`PivotTreeBuilder` has synthesised the missing parents (the table stores leaves only) and
recomputed the ratio columns. Nothing downstream aggregates again.

Commands that depend on recomputing are disabled rather than left to do nothing: Edit
Pivot, Include Pivot, Apply Tolerances, Sub-totals, Refresh and Clear Filters. Export to
Excel and Show Breaks are disabled too, since both reach for a live session's objects; see
`DashboardPivotPanel.setBreakHandler` for the hook.

One loose end remains on this seam:

- **Column names** come from `PivotMetric.label()`. The shipped view files only select
  columns whose names they know, so these need aligning with what `PivotBuilder` produces
  for the views to apply. Until they do, the breakdown renders with unformatted values —
  the numbers are right, the decimals and thousands separators come from the view.

Two that are now closed:

- **`ExcelNode`'s construction API** was assumed and was wrong. `new ExcelNode(key)` leaves
  the children map null, so the first `addChild` threw and the reconciliation screen failed
  to open. Only `ExcelNode(key, values)` initialises it, which is why `ExcelNodeBuilder`
  builds every node that way and gives its root `null` values. `DashboardPivotNodes` now
  does the same. Its other runtime question — whether `ExcelSortableTreeTableNode` mirrors
  the subtree of the node it wraps — was checked against the real class: it does not, so the
  wrapper pass runs, which is what the guard there already expected.
- **Refresh**, **Show Breaks** and **Include Pivot** are greyed out now that
  `PanelPivotActionBar` exposes `getButtonRefresh()`, `getButtonViewBreak()` and
  `getButtonIncludePivot()`. Every command in `UNSUPPORTED_COMMANDS` is disabled by command
  as well, so a new one added to that set is greyed without a matching accessor.

## The one field the dashboard writes

A batch's description is editable from the batch header, through the DAO's
`updateBatchDescription`. Everything else on these screens is a reading surface, which is why
the field does not simply sit there editable waiting to catch a stray keystroke: a pencil
arms it, a tick commits, a cross puts back what was there, and Escape is the cross. That is
how EasyRec's own option panels edit a single field - see
`DefaultOptionPanel.createEditButton`.

Three details are deliberate. The field goes back to read-only only once the write has
returned, so a failed save leaves the operator looking at what they typed rather than at the
old value with their edit silently gone. An update that changes no rows is reported rather
than passed off as a save, because a batch that has been purged under the screen is exactly
when that happens. And the prompt for an empty description is a FlatLaf placeholder rather
than a `HintTextField`, whose `getText` reports an empty string whenever the text equals its
hint - which would have saved a batch actually described as "No description" as nothing.

There is no permission check: `UserRightsUtils` lives in the EasyRec module this one does not
depend on. If the dashboard moves inside it, this is the place to ask.

## The traps carried over from the Django code

These are the things that would silently produce wrong numbers if the port had been
mechanical. Each is enforced in one place here rather than repeated.

**The two STATUS mappings are opposite.** `ER_DASHBOARD_BATCH` and `ER_DASHBOARD_RUN`
record how the execution finished (1 clean, 0 failed). `ER_DASHBOARD_STAT_ROWS` and
`ER_DASHBOARD_RUN_CONTEXT` record a reconciliation's outcome (0 no breaks, 1 breaks).
`StatusScope` forces every call site to name its table, so a code can't be read without
saying which mapping applies. `-1` is an execution error in both and gets its own colour.

**An execution error is on the context row, not the statistics row.** The two reconciliation
tables only cover the three outcomes together: `ER_DASHBOARD_STAT_ROWS` says whether a
reconciliation that ran found breaks, but one whose execution did not complete never got as
far as writing a statistics row, and the `-1` that says so is in `ER_DASHBOARD_RUN_CONTEXT`.
Reading the statistics alone reports an execution error as "no status recorded" - the one
outcome an operator most needs to see. `Reconciliation.status` takes an error from either
side first, then the statistics for anything that ran, then the context row.

**Statistics live one TEMPLATE_ID along.** `ER_DASHBOARD_TEMPLATE` holds two rows per
reconciliation on consecutive ids; the context table points at the first, the statistics
tables at the second. `TemplateIds.resolve` prefers an exact match and falls back to the
`+1` pairing, so a deployment that writes them consistently keeps working.

**One definition of match rate.** `matched / max(rows_source, rows_target)`, counts summed
before dividing. Averaging per-reconciliation rates would weight a two-row template like a
two-million-row one. `Rates.matchRate` is the only place it is computed.

**Ratios cannot be summed up the pivot tree.** A synthesised parent recomputes
`UNMATCH_IMPACT_PCT` from its own summed numerator and denominator; see
`PivotMetric.derivedFrom()` and `Rates.impactRatio`.

**`FULL_PATH`, not `TEMPLATE_ID`, identifies "the same reconciliation" across batches.**
EasyRec allocates fresh template rows per run, so ids differ between batches for the same
file. `CompareService` and `HistoryService` both key on the path.

**History means "the same suite run again".** A batch qualifies only when it covers every
one of the reference template paths, and is then measured over those paths alone. A batch
that ran half of them would read as a collapse in volume rather than as a smaller batch.

**`<Undefined>` is not a value.** EasyRec fills unused context columns with it, so it would
appear as a real project name wherever context is shown. `ContextValues.clean` removes it.

**SYS_DATE is an epoch-millisecond string in a character column.** No cast is portable
across Oracle, H2 and DuckDB, and one unparseable row would fail the statement, so date
filtering happens in Java over `ER_DASHBOARD_BATCH`, which is the small table. Everything
else is queried by id.

## Verification

The `service` and `dao` layers were checked against the sample database shipped with the
Django project (14 batches, 322 reconciliations, 4 196 column-stat rows, 571 pivot rows).
A harness printing every computed figure was diffed against an independent Python
implementation of the same formulas: **1 207 lines, byte-identical**, covering every batch
summary, every reconciliation and its resolved template id, every pivot tree node and its
folded metrics, the comparison rows with spread and delta, and the history trend for all
14 batches.

`PivotFoldTest` additionally exercises the folding table model against real trees: default
fold, full expansion, root-total equals leaf-total, and the root filter.

The Swing layer now compiles against the real workspace projects — `JCommon`, `JFontIcons`
and `JxTableGrid` — rather than against stubs, and every screen has been run and rendered:
home, batch list, batch detail, reconciliation (column statistics and the pivot breakdown),
comparison and history, in both `FlatLightLaf` and `FlatDarkLaf`. That is what turned up the
`ExcelNode` defect above, a MigLayout row on the batch list that shrank its two date fields
to a few pixels, field labels clipped by one pixel, and - once the tables became
JxTableGrid's - the grid's ascending default sort silently reordering every screen, the
shared packer sizing headers by character count, and the history screen's current-row
highlight bleeding onto every row below it (`DefaultTableCellRenderer.setForeground` also
sets the colour it falls back to for unselected rows, so a renderer that colours one row
conditionally has to colour the others explicitly).

## Following EasyRec's own GUI conventions

The screens are built from the same pieces the rest of the application is, so a dashboard
tab reads as part of EasyRec rather than as a panel with ideas of its own. Three helpers
carry it, each modelled on a class that already exists:

| Helper | Modelled on | What it fixes in one place |
|---|---|---|
| `ui/component/Sections` | `DefaultOptionPanel` | The titled section — `TextField.background` fill, rounded `Component.borderColor` line inside a titled border, `insets 8 12 8 12` — plus the italic grey hint, the borderless `StyledIconButton`, and the action bar's `" \| "` separator and pushing spacer |
| `ui/component/DashboardIcons` | `EasyRecIcons` | One catalogue of font icons, built through a helper that returns null on failure so a missing icon font cannot take the tab down with it |
| `ui/component/DashboardTable` | `UneditableExcelTable` | JxTableGrid's `ExcelTable` in a `JViewer` - see below |
| `ui/component/Renderers` | `StandardCellRenderer` and its family | Every dashboard renderer is one of the grid's, so a column of ours carries the same font, padding, alignment and focus behaviour as a column left to the grid's own |
| `ui/component/StatusBadge` | the `StatusBadge` sample | The Status column as a filled pill rather than a coloured word |

### The tables are JxTableGrid's, not SwingX's

Every table is `ExcelTable` inside a `JViewer`, the pair `PanelPivot` puts round the pivot
breakdown, rather than a `JXTable` in a scroll pane. The reconciliation screen shows the two
side by side and they now read as one component: column statistics above, pivot below, the
same filter menu on every header, the same status bar underneath.

What that brings, none of it written here: an Excel filter menu per column offering that
column's distinct values, column show and hide, the right-click column menu, the search
dialog on Ctrl-F, the row count and the export and clear-filter actions in the status bar.

### The renderers are the grid's too

`IntegerCellRenderer`, `PercentCellRenderer` and `NumberCellRenderer` are each a few lines
configuring a `ColumnAttribute` on top of `StandardCellRenderer`. The dashboard's renderers
are now the same shape, which fixed a mismatch that had been visible since the tables became
`ExcelTable`s: a column with a renderer of ours was set several points smaller than the
column beside it that had none.

Colour is the one thing taken back from `ColumnAttribute`, which caches `UIManager` colours
when it is constructed and would otherwise freeze a renderer's background at whichever theme
was installed when the screen was built. `StandardCellRenderer.setColor` is the hook the grid
provides for deciding a cell's colour, so per-value colouring is an override rather than
something bolted onto `DefaultTableCellRenderer` - whose `setForeground` doubles as "and use
this for every unselected row from now on", which is how a conditional colour leaks down a
column.

**Status is a badge.** PASSED, FAILED and ERROR are three words on three colours, not two
words and a silence. Status is the column the eye goes to first on three of these screens,
and a coloured word is a weak signal for it: at a glance it is the same shape as every other
cell, and red-on-white against green-on-white asks the reader to distinguish two hues of
text. `StatusBadge` came from a sample as five hard-coded pastel pairs - a light theme
written down - and three things changed to make it a cell renderer: the pill is sized to its
text rather than to the component, it paints the cell behind itself because a renderer is
never added to a container, and each colour is now the status's own semantic colour mixed
towards the surface behind it, which lands on the original pastels under FlatLaf Light and on
legible tints under FlatLaf Dark from one formula.

The mapping keeps a distinction the tables make: a run that finished and found breaks is
FAILED, a run that could not finish is ERROR in its own amber rather than a second kind of
red, and a status that was never recorded gets no pill at all - an absent value is not an
outcome.

Match rates are drawn by the grid's own `PercentCellRenderer`, which carries the locale, the
two fraction digits, the grouping and the alignment every other percentage column in the
application is typed with. It formats a ratio, since `NumberFormat.getPercentInstance`
multiplies by a hundred on the way out, so the value is divided at the renderer rather than
changing what a match rate means to the KPI tiles, the charts and the comparison.

**Type size lives in `ui/component/Fonts`.** Three places decide it and none of them agree:
`ColumnAttribute` hard-codes `SANS_SERIF` at 11 for cells, `VerticalTableHeaderCellRenderer`
bakes the theme's family bold at 11 into its no-argument constructor, and `ZoomRenderer`
overrides both from the row height. `Fonts.applyTo(table)` sets all three, and is called on
the dashboard's own grids and on the `ExcelTreeTable` the pivot breakdown is drawn on -
including that tree's hierarchical column, which SwingX paints with a `JXTree` of its own that
answers to neither.

The size is an offset from `Table.font`, not a number, so setting that font stays the one
lever a host has over every table at once. It has to be read and applied here because setting
it does not otherwise reach these tables: `ColumnAttribute` never consults it, and under any
look and feel but FlatLaf the zoom wrapper overrides whatever it says.

The shipped offset is zero - the dashboard's tables are `Table.font`. It was -2 for a while,
on the reasoning that a dashboard is a lot of table on one page and could stand to be denser
than the rest of the application. That reasoning holds only while nobody has said otherwise,
and a host that sets `Table.font` has said otherwise; subtracting from it puts these tables
two points under a size somebody chose on purpose. `SIZE_OFFSET` is still there for a
deployment that wants them denser than their neighbours. Measured, with and without a host
`UIManager.put("Table.font", …deriveFont(10f))`:

| look and feel | `Table.font` | `Table.rowHeight` | painted | with the host's 10f |
|---|---|---|---|---|
| FlatLaf Light | Segoe UI 12 | 20 | 12.0 | 10.0 |
| Metal | Dialog 12 | *(undefined)* | 12.0 | 10.0 |
| Windows | Tahoma 11 | *(undefined)* | 11.0 | 10.0 |
| Nimbus | SansSerif 12 | *(undefined)* | 12.0 | 10.0 |

**Row height decides type size, and that is settled in `prepareRenderer`.** `ZoomRenderer`
rewrites every cell's font to `rowHeight - 4 + (size - 12)` unless the row height is exactly
the value `UIManager.getInt("Table.rowHeight")` held **when that class was first loaded**.

`Table.rowHeight` is a FlatLaf key. Metal, Windows and Nimbus do not define it, so that
constant is 0 under all of them - and no table is ever nought pixels tall, so the wrapper is
permanently on and the row height decides the type size outright, whatever `Table.font` says.
Under FlatLaf the two agree, the wrapper stands aside, and the fonts set here are the ones
painted. That is the whole of the difference between "it works under FlatLaf" and "it does
not". Measured, at a theme row height of 20:

| row height | cell font, before | after |
|---|---|---|
| 20 | 10.0 | 10.0 |
| 22 | 16.0 | 12.0 |
| 24 | 18.0 | 14.0 |

`Fonts.sized` settles it from a base and the distance between the row height in use and the
theme's own, applied in `prepareRenderer` - the last word before a cell is painted, and the
only point below the zoom wrapper. At the theme's height the size is exactly the base, which
is where the dashboard sets its own tables; a ctrl-wheel zoom, which works by growing the row
height, still grows the text; and a theme scaled for a high-DPI screen scales both terms.
`DashboardTable.pack` measures through `prepareRenderer` too, so a column is sized in the type
it is painted in. The pivot's `ExcelTreeTable` gets the same treatment through
`DashboardPivotTable`, since it sits directly under a dashboard grid on the reconciliation
screen.

`ExcelTable` holds its cells rather than asking for them, so the five models moved from
`AbstractTableModel` onto `ExcelTableModel` through a shared `DashboardTableModel<T>`. Each
one still declares its columns and computes one cell - now `valueOf(record, column)` instead
of `getValueAt(row, column)` - and the records behind the rows are kept alongside, so a
double-clicked row still leads back to its batch or reconciliation. Rows are replaced
without a structure change, which is what lets a column width, a sort and a filter survive a
reload.

Five things had to be set against the grid's defaults, each for a reason worth keeping:

- **The default sort is cleared.** `ExcelRowSorter` is built with an ascending sort key on
  every column, so a table adopting it sorts itself by its first column. Every one of these
  tables arrives ordered by something the screen means - batches newest first, comparison
  rows by widest spread, column statistics worst-matching first - and that ordering is the
  answer the screen was opened for.
- **Cell spanning is off.** The grid merges equal neighbouring cells, which is right for a
  report and wrong here: merged cells would say two batches share a user by drawing them as
  one row, and the repetition they hide is what a dashboard is read for.
- **Columns are packed here, not by `TablePacker`.** The shared packer measures a header
  whose value is a `JLabel` - which is every header in this grid, since `ExcelTable`
  decorates them - by its character count rather than its pixel width, so "Match Rate" asks
  for ten pixels and renders as "Mat...".
- **The resize mode is decided per load.** Auto-resize squeezes the batch list's seventeen
  columns into whatever the window is; no auto-resize strands the five-column comparison
  against a band of empty grey. Which is right depends on the data, so it is answered after
  each pack.
- **Counts render as whole numbers.** The grid formats every `Number` to two decimals, which
  is right for an amount and wrong for a count: a reconciliation count reading "5.00" claims
  a precision the figure does not have.

`JViewer` refreshes its status bar from a `TableModelListener`, and Swing notifies that
listener before the table itself has taken the change - so on a screen that loads after it
is built the count reads zero forever. Every screen calls `Tables.refresh` once a load has
landed, which packs the columns and updates the count.

**The batch list keeps only its date filter.** User, Project, Source and Target had
dropdowns of their own; every one of them is also a column of that table, and the grid's
header menus filter a column by its distinct values - so the dropdowns were a second, worse
way to do what the column above them does, and one that cost a query against
`ER_DASHBOARD_RUN_CONTEXT` on first load. Date stayed because it is the one that could not
move: the table shows a batch's date as text, so a header filter over it would pick values
rather than a range, and `SYS_DATE` is an epoch-millisecond string read in Java anyway.
`DashboardService.filterOptions` and the two DAO queries behind it are left in place but are
now called by nothing.

What that changed, screen by screen: every screen now opens with an action bar laid out like
`PanelPivotActionBar`; the header carries a clickable breadcrumb, so a drill-down four levels
deep can be walked back a level at a time rather than only with Back; filters, context and
each table live in a titled section; the period buttons are a `SegmentedControl` - one
track with three positions rather than three separate buttons, since that is what they are;
and the KPI tiles carry an icon and a caret on their movement.

The segmented control paints its own track and its own chosen pill. A toggle button's
selected state is drawn differently by FlatLaf, Metal and Windows, and only FlatLaf's is
emphatic enough to answer "which period am I looking at" across a desk. The chosen segment
gets an outline as well as a fill, because under a look and feel whose border tone is already
close to the accent - Metal's is blue - the two fills come out within a few values of each
other and the chosen segment stops looking chosen.

`Palette.border` had the same shape of problem as the fonts: `Component.borderColor` is a
FlatLaf key, and falling back to a literal under Metal, Windows and Nimbus gave the dashboard
a dark slate frame on a light grey desktop. `controlShadow` is the border tone all three do
define - #b8cfe5, #a0a0a0, #ccd3e0 - so it is asked second, and `Sections` now takes its line
from the same place rather than keeping a second fallback of its own.

`Palette.muted()` resolves from `UIManager` on every call. It used to return
`GuiPreferences.DARK_GRAY`, which is a `static final` bound to whichever theme was installed
when that class first loaded — enough to freeze every caption colour at start-up and leave
them unreadable after a theme switch, which is the one thing `Palette` exists to prevent.

## Not carried over

- **Editing a reconciliation's context.** `updateRunContext` exists on the DAO with the
  right constraints - it binds both `RUN_ID` and `TEMPLATE_ID`, since that table has no
  primary key and a `RUN_ID`-only update rewrites every context row of the run - but nothing
  calls it yet. The batch description is the one field the dashboard writes; see below.
- **Batch deletion.** `deleteBatches` is implemented, children first, in one transaction,
  and deliberately leaves `ER_DASHBOARD_TEMPLATE` alone. No UI yet.
- **Pagination.** The batch list reads the whole table, as the Django version effectively
  did at 100 rows. If a production deployment has thousands of batches this is the first
  thing to revisit.
