package me.vripper.gui.fx

import javafx.event.EventTarget
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.ToolBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

fun EventTarget.vbox(
    spacing: Number? = null,
    alignment: Pos? = null,
    op: VBox.() -> Unit = {},
): VBox {
    val vbox = VBox()
    if (spacing != null) vbox.spacing = spacing.toDouble()
    if (alignment != null) vbox.alignment = alignment
    return attachTo(this, vbox, op)
}

fun EventTarget.hbox(
    spacing: Number? = null,
    alignment: Pos? = null,
    op: HBox.() -> Unit = {},
): HBox {
    val hbox = HBox()
    if (spacing != null) hbox.spacing = spacing.toDouble()
    if (alignment != null) hbox.alignment = alignment
    return attachTo(this, hbox, op)
}

fun EventTarget.stackpane(
    initialChildren: Iterable<Node>? = null,
    op: StackPane.() -> Unit = {},
): StackPane {
    val pane = StackPane()
    if (initialChildren != null) pane.children.addAll(initialChildren)
    return attachTo(this, pane, op)
}

fun EventTarget.borderpane(op: BorderPane.() -> Unit = {}): BorderPane =
    attachTo(this, BorderPane(), op)

fun EventTarget.scrollpane(
    fitToWidth: Boolean = false,
    fitToHeight: Boolean = false,
    op: ScrollPane.() -> Unit = {},
): ScrollPane {
    val pane = ScrollPane()
    pane.isFitToWidth = fitToWidth
    pane.isFitToHeight = fitToHeight
    return attachTo(this, pane, op)
}

fun EventTarget.splitpane(
    orientation: Orientation = Orientation.HORIZONTAL,
    vararg nodes: Node,
    op: SplitPane.() -> Unit = {},
): SplitPane {
    val pane = SplitPane()
    pane.orientation = orientation
    if (nodes.isNotEmpty()) pane.items.addAll(nodes)
    return attachTo(this, pane, op)
}

fun EventTarget.toolbar(vararg nodes: Node, op: ToolBar.() -> Unit = {}): ToolBar {
    val toolbar = ToolBar()
    if (nodes.isNotEmpty()) toolbar.items.addAll(nodes)
    return attachTo(this, toolbar, op)
}

fun EventTarget.separator(
    orientation: Orientation = Orientation.HORIZONTAL,
    op: Separator.() -> Unit = {},
): Separator = attachTo(this, Separator(orientation), op)

fun EventTarget.tabpane(op: TabPane.() -> Unit = {}): TabPane = attachTo(this, TabPane(), op)

fun TabPane.tab(text: String, op: Tab.() -> Unit = {}): Tab {
    val tab = Tab(text)
    tabs.add(tab)
    op(tab)
    return tab
}

fun TabPane.tab(uiComponent: UIComponent, op: Tab.() -> Unit = {}): Tab {
    val tab = Tab()
    tab.content = uiComponent.root
    tab.textProperty().bind(uiComponent.titleProperty)
    tabs.add(tab)
    op(tab)
    return tab
}
