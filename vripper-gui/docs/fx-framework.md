# VRipper GUI FX Layer

`me.vripper.gui.fx` is the small JavaFX support layer used by the VRipper desktop
application. It replaces the previously embedded TornadoFX source. It is not a
general-purpose framework and it is deliberately smaller than one.

The layer has three jobs:

1. Own the application singleton/component lifecycle.
2. Provide a Kotlin DSL for assembling JavaFX nodes.
3. Fill a few gaps where JavaFX requires several lines of boilerplate for a
   common operation.

Everything returned by the DSL is a normal JavaFX type (`VBox`, `Button`,
`TableView`, …). There is no parallel UI model.

---

## Architecture at a glance

```
VripperGuiApplication : javafx.application.Application
        |
        |  register primary stage, find LoadingView, show scene
        v
FX (global runtime)                    Component (lifecycle + DI)
  - component cache                     - find/inject/param/di
  - construction params                 - primaryStage
  - primary stage                       - children helpers
        |                                      |
        +-------------------+------------------+
                            |
                    UIComponent / View / Fragment / Controller
                            |
                    DSL: Layouts / Controls / Nodes / Tables / Forms / Menu
                            |
                       JavaFX nodes
```

The application currently has **one primary stage and one component cache**.
An earlier `Scope`/`App` abstraction was removed because VRipper has exactly one
application instance. Do not reintroduce multi-application scoping unless the
application actually gains a second, independent UI root.

---

## Files and responsibilities

| File | Responsibility | Current size |
| --- | --- | ---: |
| `Fx.kt` | Global runtime, component cache, construction params, `find`, `runLater`, debug log. | 82 lines |
| `Component.kt` | `Component`, `UIComponent`, `View`, `Fragment`, `Controller`, modal lifecycle, `attachTo`, child dispatch. | 278 lines |
| `Binding.kt` | Bidirectional/one-way bind helpers for common controls. | 29 lines |
| `Properties.kt` | `by` property delegates, typed `onChange`, `isBlank`/`isNotBlank`. | 62 lines |
| `Nodes.kt` | Styling, tooltips, visibility/enabled bindings, growth aliases, spacers. | 112 lines |
| `Layouts.kt` | Container builders (`vbox`, `hbox`, `borderpane`, `scrollpane`, tabs, …). | 101 lines |
| `Controls.kt` | Typed control builders (`label`, `button`, `textfield`, …). | 175 lines |
| `Forms.kt` | `Form`, `Field`, `Fieldset` layout helpers. | 61 lines |
| `Tables.kt` | `SortedFilteredList`, table/list builders, column helper. | 85 lines |
| `Dialogs.kt` | Alert, confirmation, file/directory chooser helpers. | 93 lines |
| `Menu.kt` | Menu bar/menu/item builders. | 54 lines |

Total: approximately **1,130 lines**. The exact line count is not the goal; the
goal is that each file has a clear, repeated use case.

---

## Runtime and component model

### `FX`

`FX` is the only global runtime object:

```kotlin
object FX {
    internal val components = ConcurrentHashMap<KClass<*>, ScopedInstance>()
    internal val inheritParamHolder = ThreadLocal<Map<String, Any?>?>()

    internal fun registerPrimaryStage(stage: Stage)

    val primaryStage: Stage?
}
```

- `components` caches singleton components by class.
- `inheritParamHolder` makes `by param()` work during construction without
  changing constructors.
- `primaryStage` is set once by `VripperGuiApplication` before the primary view
  is created.

### Component types

| Type | Cached? | Purpose |
| --- | --- | --- |
| `Controller` | Yes | Non-visual singleton/application controller. |
| `View` | Yes | Long-lived screen or major panel. |
| `Fragment` | No | One-off screen/dialog, usually with parameters. |
| `UIComponent` | N/A | Base class for `View` and `Fragment`. |

`View` and `Controller` implement the marker `ScopedInstance`; `Fragment` does
not. That marker is the only thing that decides whether `find()` caches an
instance.

### `find`, `inject`, `param`, `di`

```kotlin
private val postsController: PostsTabController by inject()

val id: Long by param()
val title: String by param("")
val service: RemoteAppEndpointService by di()
val namedService: RemoteAppEndpointService by di("remoteAppEndpointService")
```

Construction flow:

1. `find<T>(params)` normalizes parameter keys to strings. A `KProperty`
   reference such as `RenameFragment::id` becomes `"id"`.
2. It installs the map in `FX.inheritParamHolder` for the current thread.
3. If `T` is a `ScopedInstance`, the cached instance is returned or created;
   otherwise a new instance is created.
4. The component constructor runs. `by param()` reads the thread-local map
   snapshot into the immutable `params` property.
5. The previous thread-local value is restored.

Consequences:

- `View`, `Fragment`, and `Controller` classes **must have a no-argument
  constructor**. Use `by param()` for input, not constructor parameters.
- `by param()` is intended for `Fragment` instances. Cached views/controllers
  keep the parameters from their first construction.
- `find`/component creation must happen on the thread that constructs the
  component (normally the JavaFX thread).
- `by di()` is lazy and resolves from Koin’s `GlobalContext` on first access.

### `UIComponent` lifecycle

`UIComponent` owns a `root: Parent` and provides:

| Member | Meaning |
| --- | --- |
| `titleProperty` / `title` | Window/tab title; setters unbind a previous binding. |
| `currentWindow` / `currentStage` | Modal stage, containing window, or primary stage. |
| `onBeforeShow()` | Called before a modal is shown or a view is replaced. |
| `onDock()` / `onUndock()` | Called when a modal becomes visible/hidden. |
| `isDocked` | Visibility/dock flag, idempotent. |
| `openWindow(...)` | Non-modal convenience wrapper around `openModal`. |
| `openModal(...)` | Create/show/reuse a modal stage. |
| `close()` | Close the modal or current stage. |
| `replaceWith(...)` | Swap a scene root or a child in a `Pane`. |

`openModal` handles the details that plain JavaFX does not provide in one place:

- Reuses an existing `modalStage` and refuses to show it twice.
- Binds the stage title to `titleProperty`.
- Applies stage style, modality, owner and optional resizability.
- Closes on `Escape` by default.
- Copies the primary stage icons.
- Centers the dialog over its owner after layout.
- Calls `onBeforeShow`, `onDock` and `onUndock` at the right times.
- Supports blocking (`showAndWait`) or non-blocking (`show`) display.

Use `openWindow(owner = null)` for an independent, non-modal window. Use
`openModal()` for a normal modal dialog.

### Application startup

`VripperGuiApplication` now extends JavaFX `Application` directly. Its startup
sequence is:

```kotlin
FX.registerPrimaryStage(stage)
val view = find<LoadingView>()
view.onBeforeShow()
stage.titleProperty().bind(view.titleProperty)
stage.scene = Scene(view.root)
stage.show()          // fires the WINDOW_SHOWN filter, which starts Koin
view.callOnDock()
```

This keeps the primary-stage registration and primary-view lifecycle explicit.
There is no `App` base class and no `Scope`.

---

## Child attachment and the DSL core

All DSL builders ultimately call:

```kotlin
inline fun <T : Node> attachTo(
    parent: EventTarget,
    node: T,
    op: T.() -> Unit = {},
): T
```

`attachTo` attaches the node to the parent, runs the configuration lambda, and
returns the node. The receiver form (`node.attachTo(parent) { ... }`) is used by
control builders.

`addChildIfPossible` is the single dispatch point for supported parents:

| Parent | Behavior |
| --- | --- |
| `UIComponent` | Adds to its `root`. |
| `ScrollPane` | Sets `content`. |
| `Tab` | Sets `content`. |
| `SplitPane` | Adds to `items`. |
| `ToolBar` | Adds to `items`. |
| `Field` | Adds to `inputContainer`. |
| `Pane` | Adds to `children` (deduplicated). |
| `BorderPane` | Intentionally does nothing. |

`BorderPane` is a no-op because its children must be assigned to `top`,
`right`, `bottom`, `left`, or `center`. The old TornadoFX region DSL was removed
on purpose; do not reintroduce it.

Tabs are added through `TabPane.tab(...)`, not through generic `add`. This
avoids ambiguous placement and keeps tab title binding explicit.

Both member and top-level `add`/`+=` helpers exist so DSL code works inside a
`Component` (`add(child)`) and inside free-standing builders (`parent.add(child)`).

---

## Building layouts

```kotlin
import me.vripper.gui.fx.*
import javafx.geometry.Insets

class ExampleView : View("Example") {
    override val root = borderpane {
        padding = Insets(10.0)

        top = menubar {
            menu("File") {
                item("Close") { action { close() } }
            }
        }

        center = tableview<Row>(rows) {
            column("Name", Row::name)
            column("Status", Row::status)
        }

        right = vbox(spacing = 8.0) {
            button("Refresh") { action { refresh() } }
            spacer()
            button("Close") { action { close() } }
        }
    }
}
```

Available layout builders:

- `vbox(spacing, alignment)`
- `hbox(spacing, alignment)`
- `stackpane(initialChildren)`
- `borderpane`
- `scrollpane(fitToWidth, fitToHeight)`
- `splitpane(orientation, vararg nodes)`
- `toolbar(vararg nodes)`
- `separator(orientation)`
- `tabpane`
- `TabPane.tab(text)` and `TabPane.tab(uiComponent)`

Use JavaFX properties directly for `BorderPane` regions:

```kotlin
top = ...
center = ...
bottom = ...
left = ...
right = ...
```

### Controls

Control builders are thin wrappers that create the control, attach it, apply
optional binding, and run a configuration lambda:

```kotlin
label("Name")
label(model.nameProperty)
textfield(model.nameProperty)      // bidirectional by default
passwordfield(model.passwordProperty)
textarea(model.notesProperty)
// For a read-only binding, use the bind helper directly:
// textfield().apply { bind(model.nameProperty, readonly = true) }
button("Save") { action { save() } }
button(model.actionLabelProperty)
checkbox("Enabled", model.enabledProperty)
combobox(model.choiceProperty, listOf("A", "B"))
radiobutton("Option", group, value = 1)
hyperlink("Open", graphic = icon)
imageview("icons/logo.png")
progressindicator()
text("Plain text")
```

Binding rules in `Binding.kt`:

- If the source is a JavaFX `Property` and `readonly` is `false`, the control
  property is bound **bidirectionally**.
- If the source is read-only or `readonly = true`, it is bound one-way.
- `label`/`text` with an `ObservableValue<String>` are always one-way.

---

## Reactive helpers

### Property delegates

```kotlin
val name: String by model.nameProperty
var threshold: Double by model.thresholdProperty

model.nameProperty.onChange { newName -> ... }
model.countProperty.onChange { count -> ... }
list.onChange { change -> ... }
```

`Properties.kt` supplies typed `onChange` overloads for generic, boolean,
integer, long, float, and double observables. The generic overload passes the
new value (nullable).

### Visibility and state

```kotlin
node.visibleWhen(model.visibleProperty)
node.managedWhen(model.visibleProperty)
node.disableWhen(model.busyProperty)
node.enableWhen(model.readyProperty)
textfield.editableWhen(model.editableProperty)
menuItem.visibleWhen(model.visibleProperty)

model.nameProperty.isBlank()
model.nameProperty.isNotBlank()
```

`isBlank`/`isNotBlank` return JavaFX boolean bindings, so they compose with
`visibleWhen`, `disableWhen`, and `bind`.

### Styling and growth

```kotlin
label("Title").addClass("title")
node.tooltip("Explanation")
node.hgrow = Priority.ALWAYS
node.vgrow = Priority.ALWAYS
hbox.spacer()
vbox.spacer()
toolbar.spacer()
```

`spacer()` creates a growable `Pane` (or `Region` in a `ToolBar`) and attaches
it to the parent.

---

## Forms

`Form`, `Fieldset`, `Field`, and `FormGrid` provide a small settings-form
structure without introducing a form model:

```kotlin
form {
    fieldset("Connection") {
        field("Host:") {
            textfield(model.hostProperty)
        }
        field("Port:") {
            textfield(model.portProperty)
        }
    }
}
```

- `FormGrid` is a two-column `GridPane` where column 0 is sized to the widest
  label and column 1 grows. This is the JavaFX equivalent of a CSS grid form
  with `grid-template-columns: max-content 1fr`; every value starts at the same
  horizontal position. Use `addRow(label, value)` or `addSpanningRow(node)` for
  hand-built forms.
- `Fieldset` extends `FormGrid` and adds an optional title row. Fields are
  placed in the shared grid automatically, so all `field(...)` values in the
  same fieldset are aligned. Nested fieldsets inherit the outer label-column
  width, keeping their values aligned with the parent section.
- `Field` is a logical `HBox` row with `labelContainer` and `inputContainer`.
  When added to a `Fieldset`, its two containers become the aligned grid cells;
  its `visible`/`managed` properties still control the whole row.
- `Form` is a `VBox` with 12 px spacing for stacking fieldsets.

These classes are used heavily by the settings screens. Replacing them with
hand-built `HBox`/`VBox` code would duplicate alignment/growth logic in dozens
of places.

---

## Tables and filtering

`SortedFilteredList<T>` wires the three-list JavaFX pattern in one place:

```kotlin
val rows = SortedFilteredList<Row>()       // items: mutable backing list
rows.items.addAll(data)                    // mutate the backing list
rows.predicate = { it.matches(query) }     // filter

tableview(rows) {
    column("Name", Row::name)
}
```

Internals:

| Property | Type | Meaning |
| --- | --- | --- |
| `items` | `ObservableList<T>` | Source list; write here. |
| `filteredItems` | `FilteredList<T>` | Filtered view of `items`. |
| `sortedItems` | `SortedList<T>` | Sorted view of `filteredItems`. |
| `predicate` | `((T) -> Boolean)?` | Updates `filteredItems.predicate`. |

The wrapper itself delegates `ObservableList` reads to `sortedItems`, so it is
read-only at the wrapper level. **Do not call `rows.add(...)` or
`rows.remove(...)`; mutate `rows.items` instead.** `FilteredList` automatically
re-evaluates new/changed source items, so no manual `refilter()` is needed.

`bindTo(TableView)` sets the table items and binds its comparator to the sorted
list. `tableview(items)` does this automatically when it receives a
`SortedFilteredList`.

`column("Name", Model::nameProperty)` expects a property reference, not a plain
value. The property must return an `ObservableValue<T>` (for example
`StringProperty`, `IntegerProperty`, `ObjectProperty<T>`).

`listview(values)` follows the same binding rules for lists.

---

## Menus and dialogs

```kotlin
menubar {
    menu("File") {
        item("Open", KeyCombination.keyCombination("Shortcut+O")) {
            action { open() }
        }
        separator()
        checkmenuitem("Auto refresh", selectedProperty = model.autoRefreshProperty)
    }
}
```

Dialog helpers are used instead of repeatedly constructing JavaFX `Alert` and
chooser objects:

```kotlin
confirm("Delete post?", "This cannot be undone.", owner = currentWindow) {
    delete()
}

information("Done", "Import completed.", owner = currentStage)

alert(Alert.AlertType.ERROR, "Invalid input", e.message)

val file = chooseFile(
    title = "Choose archive",
    filters = arrayOf(FileChooser.ExtensionFilter("Archives", "*.zip", "*.cbz")),
    owner = currentStage,
)

val directory = chooseDirectory(title = "Choose folder", owner = currentStage)
```

- `confirm` runs the action only for the confirming button.
- `information` supports custom button sets.
- `alert` is a minimal alert wrapper with an optional result callback.
- Choosers return `null`/empty list when cancelled.

---

## Threading

`runLater` is intentionally not just `Platform.runLater`:

```kotlin
fun runLater(op: () -> Unit) {
    if (Platform.isFxApplicationThread()) {
        op()
    } else {
        Platform.runLater(op)
    }
}
```

- Already on the FX thread: execute immediately. This avoids reordering and
  keeps state updates synchronous in UI callbacks.
- Off the FX thread: queue on the FX thread.

Use `runLater` for UI mutations from coroutines, gRPC callbacks, and background
workers. Do not use `Platform.runLater` directly in this module unless you
specifically need asynchronous ordering.

---

## Testing and verification

The module is normally verified with:

```bash
mvn -o -pl vripper-gui clean compile -DskipTests
mvn -o -pl vripper-gui -am clean package -DskipTests
mvn -o -pl vripper-core test
```

A JavaFX smoke test can exercise views, tabs, tables, and `SortedFilteredList`
without starting the full application. Run it with the software renderer when
no GPU is available:

```bash
java -Dprism.order=sw -cp "<smoke-classes>:<gui-classes>:<classpath>" SmokeKt
```

---

## What remains and why it should stay

The remaining code is not “unused TornadoFX residue.” It is a deliberately
small compatibility layer. The criterion for keeping a helper is **repeated,
specific value at call sites**, not “TornadoFX had it.”

| Area | Why it remains | Why reducing it further is not worth it |
| --- | --- | --- |
| `FX` component cache / `find` | `View`, `Fragment`, and `Controller` wiring depends on it. | Removing it would push reflection, caching, and param threading into every caller. |
| `ScopedInstance` marker | Distinguishes cached `View`/`Controller` from per-use `Fragment`. | A boolean or naming convention would be less explicit and easier to misuse. |
| `Component.param`/`di` | Fragments need typed construction-time parameters; Koin needs typed lookup. | Constructor injection is not viable with `find<T>()`’s no-arg construction. |
| `UIComponent` lifecycle | `openModal`, `onDock`, `onUndock`, `currentStage`, and `replaceWith` encode real window behavior. | Plain JavaFX requires repeating stage setup, owner centering, escape handling, and callbacks in every dialog. |
| `attachTo` / `addChildIfPossible` | One consistent attachment rule for every builder. | Scattering `when (parent)` logic across builders would create inconsistent behavior. |
| Layout/control builders | Used throughout the view layer; they preserve the existing Kotlin DSL style. | Replacing them with constructors would inflate every view and make diffs/noise much larger. |
| `Form`/`Field`/`Fieldset` | Settings and detail screens depend on the label/input alignment. | Reimplementing alignment, growth, and spacing in every form is repetitive and error-prone. |
| `onChange`, delegates, visibility helpers | Hundreds of call sites; typed convenience is the main value. | The bodies are tiny, but removing them would add listener boilerplate everywhere. |
| `Binding.kt` | Encodes “bidirectional unless read-only” for common controls. | JavaFX’s default is not expressive enough in one line; call sites would need conditionals. |
| `SortedFilteredList` | Correctly wires `FilteredList` + `SortedList` + table comparator. | Each table would otherwise duplicate list composition, comparator binding, and predicate updates. |
| `column`, `tableview`, `listview` | Centralize table/list setup and property-reference cell factories. | Manual `TableColumn`/`setCellValueFactory` code is longer and easier to get wrong. |
| `Dialogs` | Alerts and choosers are verbose and repeated. | Direct JavaFX code is 5–10 lines per use; the wrappers keep call sites readable. |
| `runLater` | Immediate execution on the FX thread is relied upon. | `Platform.runLater` always queues and changes callback ordering; the helper is 7 lines and prevents subtle bugs. |
| `FxLog` | Lazy debug message construction and a single logger. | It is tiny and avoids string construction when debug is off. |

### What was intentionally removed

These were removed because they were either unused, duplicated JavaFX, or added
a second way to do the same thing:

- The embedded `tornadofx` source package.
- `Scope`, `App`, `FX.defaultScope`, and per-scope primary stages.
- `builderTarget` and the `BorderPane` region DSL.
- `InlineCss`, `Dimension`, and `style { ... }`.
- `paramsProperty` (replaced by an immutable construction-time `params`).
- `insets(...)` (use `Insets(...)`).
- `ObservableList.sizeProperty` (use `Bindings.size(list)`).
- `ObservableList.sortWith` (use `FXCollections.sort` or the Kotlin standard
  library).
- `onDoubleClick` (use `setOnMouseClicked`).
- `useMaxSize` (assign `maxWidth`/`maxHeight` directly).
- Unused `addChildIfPossible` branches (`ButtonBase`, `MenuItem`, `TabPane`,
  `Group`), `UI_COMPONENT_PROPERTY`, and `UIComponent.init()`.
- Unused `FxLog.warn`/`error`.

### Rules for adding new helpers

1. Prefer plain JavaFX when the operation is used once.
2. Add a DSL helper only when the same multi-step pattern appears repeatedly.
3. Return the concrete JavaFX type, not a custom wrapper.
4. Do not add a parallel layout or styling model; compose JavaFX properties.
5. Do not add a second cache/scope system; `FX.components` is the single cache.
6. Use `View`/`Fragment`/`Controller` according to lifetime, not convenience.
7. Use parameters only with `Fragment`; keep cached components parameter-free.
8. Mutate `SortedFilteredList` through `items`.
9. Use `runLater` for UI work coming from non-FX threads.
10. If a helper is only used by one screen, keep it local to that screen.

---

## Summary

The layer is small because the application is a single-window desktop client,
not a multi-app platform. The remaining code exists to keep component lifetimes,
window behavior, parameter passing, table composition, and the Kotlin DSL
consistent. The parts that were merely TornadoFX habits, duplicated JavaFX
one-liners, or unused abstractions have already been removed.
