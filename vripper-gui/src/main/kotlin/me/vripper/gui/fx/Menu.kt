package me.vripper.gui.fx

import javafx.beans.value.ObservableValue
import javafx.event.EventTarget
import javafx.scene.Node
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.input.KeyCombination

fun EventTarget.menubar(op: MenuBar.() -> Unit = {}): MenuBar = attachTo(this, MenuBar(), op)

fun MenuBar.menu(name: String, op: Menu.() -> Unit = {}): Menu {
    val menu = Menu(name)
    menus.add(menu)
    op(menu)
    return menu
}

fun Menu.item(
    name: String,
    keyCombination: KeyCombination? = null,
    graphic: Node? = null,
    op: MenuItem.() -> Unit = {},
): MenuItem {
    val menuItem = MenuItem(name)
    if (keyCombination != null) menuItem.accelerator = keyCombination
    if (graphic != null) menuItem.graphic = graphic
    items.add(menuItem)
    op(menuItem)
    return menuItem
}

fun Menu.separator(): SeparatorMenuItem {
    val separator = SeparatorMenuItem()
    items.add(separator)
    return separator
}

fun Menu.checkmenuitem(
    name: String,
    keyCombination: KeyCombination? = null,
    selectedProperty: ObservableValue<Boolean>? = null,
    op: CheckMenuItem.() -> Unit = {},
): CheckMenuItem {
    val menuItem = CheckMenuItem(name)
    if (keyCombination != null) menuItem.accelerator = keyCombination
    if (selectedProperty != null) menuItem.bind(selectedProperty)
    items.add(menuItem)
    op(menuItem)
    return menuItem
}
