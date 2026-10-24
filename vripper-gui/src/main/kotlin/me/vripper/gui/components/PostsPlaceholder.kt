package me.vripper.gui.components

import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import org.kordamp.ikonli.feather.Feather

enum class PostsPlaceholderState {
    LOADING,
    EMPTY,
    CONNECTION_FAILURE,
}

class PostsPlaceholder(
    state: ObservableValue<PostsPlaceholderState>,
    emptyMessage: ObservableValue<String>,
) : StackPane() {

    private val loading = Label("Loading")
    private val connectionFailure = Label("Connection Failure")
    private val empty = EmptyState.create(Feather.DOWNLOAD, emptyMessage)

    init {
        alignment = Pos.CENTER
        isMouseTransparent = true
        children.addAll(loading, connectionFailure, empty)
        bindVisibility(loading, PostsPlaceholderState.LOADING, state)
        bindVisibility(connectionFailure, PostsPlaceholderState.CONNECTION_FAILURE, state)
        bindVisibility(empty, PostsPlaceholderState.EMPTY, state)
    }

    private fun bindVisibility(
        node: Node,
        target: PostsPlaceholderState,
        state: ObservableValue<PostsPlaceholderState>,
    ) {
        val visible = Bindings.createBooleanBinding({ state.value == target }, state)
        node.visibleProperty().bind(visible)
        node.managedProperty().bind(visible)
    }
}
