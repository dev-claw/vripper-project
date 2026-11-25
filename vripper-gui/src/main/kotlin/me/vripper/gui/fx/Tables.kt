package me.vripper.gui.fx

import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.event.EventTarget
import javafx.scene.control.ListView
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.util.Callback
import java.util.function.Predicate
import kotlin.reflect.KProperty1

class SortedFilteredList<T>(
    val items: ObservableList<T> = FXCollections.observableArrayList(),
    initialPredicate: (T) -> Boolean = { true },
    val filteredItems: FilteredList<T> = FilteredList(items, Predicate { initialPredicate(it) }),
    val sortedItems: SortedList<T> = SortedList(filteredItems),
) : ObservableList<T> by sortedItems {

    var predicate: ((T) -> Boolean)? = initialPredicate
        set(value) {
            field = value
            if (value != null) {
                filteredItems.predicate = Predicate { value(it) }
            }
        }

    fun bindTo(tableView: TableView<T>): SortedFilteredList<T> = apply {
        tableView.items = this
        sortedItems.comparatorProperty().bind(tableView.comparatorProperty())
    }

    fun bindTo(listView: ListView<T>): SortedFilteredList<T> = apply {
        listView.items = this
    }
}

fun <T> EventTarget.tableview(
    items: ObservableList<T>? = null,
    op: TableView<T>.() -> Unit = {},
): TableView<T> {
    val tableView = TableView<T>()
    if (items != null) {
        if (items is SortedFilteredList<T>) {
            items.bindTo(tableView)
        } else {
            tableView.items = items
        }
    }
    addChildIfPossible(tableView)
    op(tableView)
    return tableView
}

fun <T> EventTarget.listview(
    values: ObservableList<T>? = null,
    op: ListView<T>.() -> Unit = {},
): ListView<T> {
    val listView = ListView<T>()
    if (values != null) {
        if (values is SortedFilteredList<T>) {
            values.bindTo(listView)
        } else {
            listView.items = values
        }
    }
    addChildIfPossible(listView)
    op(listView)
    return listView
}

fun <S, T> TableView<S>.column(
    title: String,
    prop: KProperty1<S, ObservableValue<T>>,
    op: TableColumn<S, T>.() -> Unit = {},
): TableColumn<S, T> {
    val column = TableColumn<S, T>(title)
    column.cellValueFactory = Callback { prop.call(it.value) }
    columns.add(column)
    op(column)
    return column
}
