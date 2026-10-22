package me.vripper.gui.controller

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.scene.control.ButtonType
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.gui.components.PostsPlaceholderState
import me.vripper.gui.components.Shared
import me.vripper.gui.components.fragments.PostWorkspaceFragment
import me.vripper.gui.components.fragments.RenameFragment
import me.vripper.entities.Status
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.matchesStatus
import me.vripper.gui.utils.openFileDirectory
import me.vripper.gui.utils.openLink
import me.vripper.model.QueueState
import me.vripper.services.download.MovePosition
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

enum class PostQuickFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DONE("Done"),

    PAUSED("Paused"),
    ERROR("Error"),
}

/**
 * Shared state and actions for the posts table and grid. Both views render the
 * same [items] list, publish their selection here, and use the same context
 * menu, so switching layouts never loses the current selection.
 */
class PostsTabController : Controller() {

    private val postController: PostController by inject()
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val items: SortedFilteredList<PostModel> = SortedFilteredList()
    val selectedPosts = FXCollections.observableArrayList<PostModel>()
    val activePostProperty = SimpleObjectProperty<PostModel?>(null)
    var activePost: PostModel? by activePostProperty
    val placeholderStateProperty = SimpleObjectProperty(PostsPlaceholderState.LOADING)
    var placeholderState: PostsPlaceholderState by placeholderStateProperty
    val emptyMessage = SimpleStringProperty("No posts yet")
    val quickFilterProperty = SimpleObjectProperty(PostQuickFilter.ALL)
    var quickFilter: PostQuickFilter by quickFilterProperty

    private var sortRequest: (() -> Unit)? = null

    init {
        Shared.postsSearchInput.addListener { _, _, _ -> applyFilters() }
        quickFilterProperty.addListener { _, _, _ -> applyFilters() }
        applyFilters()
        observeUpdates()
    }

    private fun applyFilters() {
        val query = Shared.postsSearchInput.get().orEmpty().trim()
        val filter = quickFilterProperty.get() ?: PostQuickFilter.ALL
        emptyMessage.set(
            if (query.isBlank() && filter == PostQuickFilter.ALL) "No posts yet" else "No matching posts"
        )
        items.predicate = { item ->
            matchesQuery(item, query) && matchesQuickFilter(item, filter)
        }
    }

    private fun matchesQuery(item: PostModel, query: String): Boolean =
        query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.vgPostId.toString().contains(query) ||
                item.vgThreadId.toString().contains(query) ||
                item.hosts.contains(query, ignoreCase = true) ||
                item.status.contains(query, ignoreCase = true) ||
                item.path.contains(query, ignoreCase = true)

    private fun matchesQuickFilter(item: PostModel, filter: PostQuickFilter): Boolean =
        when (filter) {
            PostQuickFilter.ALL -> true
            PostQuickFilter.ACTIVE -> item.status.matchesStatus(Status.PENDING) ||
                    item.status.matchesStatus(Status.DOWNLOADING)
            PostQuickFilter.DONE -> item.status.matchesStatus(Status.FINISHED)
            PostQuickFilter.ERROR -> item.status.matchesStatus(Status.ERROR)
            PostQuickFilter.PAUSED -> item.status.matchesStatus(Status.STOPPED)
        }

    /**
     * Lets the table provide the actual sort operation (its comparator is owned
     * by the TableView). When no table is available the sorted list is sorted
     * with its current comparator.
     */
    fun bindSortRequest(request: () -> Unit) {
        sortRequest = request
    }

    fun requestSort() {
        val request = sortRequest
        if (request != null) {
            request()
        } else {
            items.sortedItems.comparator?.let { comparator -> items.sortedItems.sortWith(comparator) }
        }
    }

    fun setSelection(posts: List<PostModel>, active: PostModel?) {
        val ids = posts.map { it.id }
        if (selectedPosts.map { it.id } != ids) {
            selectedPosts.setAll(posts)
        }
        val resolvedActive = active?.takeIf { post -> post.id in ids } ?: posts.lastOrNull()
        if (activePost?.id != resolvedActive?.id) {
            activePost = resolvedActive
        }
    }

    fun updateActivePost(post: PostModel?) {
        if (activePost?.id != post?.id) {
            activePost = post
        }
    }

    fun selectedPostIds(): Set<Long> = selectedPosts.mapTo(LinkedHashSet()) { it.id }

    fun pruneSelection() {
        val validIds = items.items.mapTo(HashSet()) { it.id }
        selectedPosts.removeIf { it.id !in validIds }
        if (activePost != null && activePost?.id !in validIds) {
            activePost = selectedPosts.lastOrNull()
        }
    }

    fun removePostsByVgPostId(vgPostIds: Collection<Long>) {
        items.items.removeIf { vgPostIds.contains(it.vgPostId) }
        requestSort()
        pruneSelection()
    }

    fun startSelected() {
        val ids = selectedPostIds()
        if (ids.isEmpty()) return
        coroutineScope.launch {
            postController.start(ids.toList())
        }
    }

    fun stopSelected() {
        val ids = selectedPostIds()
        if (ids.isEmpty()) return
        coroutineScope.launch {
            postController.stop(ids.toList())
        }
    }

    fun deleteSelected() {
        val targets = selectedPosts.ifEmpty { listOfNotNull(activePost) }
        if (targets.isEmpty()) return
        val ids = targets.map { it.id }
        confirm(
            "",
            "Confirm removal of ${ids.size} post${if (ids.size > 1) "s" else ""}?",
            ButtonType.YES,
            ButtonType.NO,
            owner = FX.getPrimaryStage(scope),
            title = "Remove posts"
        ) {
            coroutineScope.launch {
                postController.delete(ids)
                runLater {
                    items.items.removeIf { ids.contains(it.id) }
                    requestSort()
                    pruneSelection()
                }
            }
        }
    }

    fun bulkRenameSelected() {
        val ids = selectedPostIds()
        if (ids.isEmpty()) return
        coroutineScope.launch {
            postController.renameToFirst(ids.toList())
        }
    }

    fun rename(post: PostModel) {
        find<RenameFragment>(
            mapOf(
                RenameFragment::id to post.id,
                RenameFragment::name to post.folderName,
                RenameFragment::altTitles to post.altTitles
            )
        ).openModal()?.apply {
            minWidth = 100.0
            minHeight = 100.0
        }
    }

    fun openOverview(post: PostModel) {
        openViewer(post, 0, startInOverview = true)
    }

    /**
     * Opens the viewer on the image matching the preview currently shown in the
     * grid tile. Falls back to the preview position when the thumbnail URL is
     * not available on the image source.
     */
    fun openPreview(post: PostModel, previewIndex: Int) {
        openViewer(post, previewIndex, startInOverview = false)
    }

    private fun openViewer(post: PostModel, previewIndex: Int, startInOverview: Boolean) {
        coroutineScope.launch {
            val imageSources = postController.getImageSources(post)
            if (imageSources.isEmpty()) return@launch
            val targetIndex = if (startInOverview) {
                0
            } else {
                val previewUrl = post.previewList.getOrNull(previewIndex)
                val matched = if (previewUrl == null) {
                    -1
                } else {
                    imageSources.indexOfFirst { it.thumbUrl == previewUrl }
                }
                if (matched >= 0) matched else previewIndex.coerceIn(0, imageSources.lastIndex)
            }
            runLater {
                PostWorkspaceFragment.openForPost(
                    post.id,
                    targetIndex,
                    startInOverview = startInOverview
                ) {
                    find<PostWorkspaceFragment>(
                        mapOf(
                            PostWorkspaceFragment::sources to imageSources,
                            PostWorkspaceFragment::initialIndex to targetIndex,
                            PostWorkspaceFragment::startInOverview to startInOverview,
                        )
                    )
                }
            }
        }
    }

    fun buildContextMenu(contextPost: () -> PostModel?): ContextMenu {
        val startItem = MenuItem("Start").apply {
            setOnAction { startSelected() }
            graphic = FontIcon.of(Feather.PLAY)
        }
        val stopItem = MenuItem("Stop").apply {
            setOnAction { stopSelected() }
            graphic = FontIcon.of(Feather.SQUARE)
        }
        val renameItem = MenuItem("Rename").apply {
            setOnAction { contextPost()?.let { rename(it) } }
            graphic = FontIcon.of(Feather.EDIT)
        }
        val bulkRenameItem = MenuItem("Auto rename").apply {
            setOnAction { bulkRenameSelected() }
            graphic = FontIcon.of(Feather.EDIT)
        }
        val deleteItem = MenuItem("Delete").apply {
            setOnAction { deleteSelected() }
            graphic = FontIcon.of(Feather.TRASH)
        }
        val locationItem = MenuItem("Open containing folder").apply {
            visibleWhen(widgetsController.currentSettings.localSessionProperty)
            setOnAction { contextPost()?.let { openFileDirectory(it.path) } }
            graphic = FontIcon.of(Feather.FOLDER)
        }
        val urlItem = MenuItem("Open link").apply {
            setOnAction { contextPost()?.let { openLink(it.url) } }
            graphic = FontIcon.of(Feather.LINK)
        }
        val overviewItem = MenuItem("Open overview").apply {
            setOnAction { contextPost()?.let { openOverview(it) } }
            graphic = FontIcon.of(Feather.GRID)
        }
        val moveToTop = MenuItem("Move to top").apply {
            setOnAction { contextPost()?.let { move(it, MovePosition.TOP) } }
            graphic = FontIcon.of(Feather.CHEVRONS_UP)
        }
        val moveUp = MenuItem("Move up").apply {
            setOnAction { contextPost()?.let { move(it, MovePosition.UP) } }
            graphic = FontIcon.of(Feather.CHEVRON_UP)
        }
        val moveDown = MenuItem("Move down").apply {
            setOnAction { contextPost()?.let { move(it, MovePosition.DOWN) } }
            graphic = FontIcon.of(Feather.CHEVRON_DOWN)
        }
        val moveToBottom = MenuItem("Move to bottom").apply {
            setOnAction { contextPost()?.let { move(it, MovePosition.BOTTOM) } }
            graphic = FontIcon.of(Feather.CHEVRONS_DOWN)
        }
        return ContextMenu().apply {
            items.addAll(
                startItem,
                stopItem,
                renameItem,
                bulkRenameItem,
                deleteItem,
                SeparatorMenuItem(),
                locationItem,
                urlItem,
                overviewItem,
                SeparatorMenuItem(),
                moveToTop,
                moveUp,
                moveDown,
                moveToBottom
            )
        }
    }

    private fun move(post: PostModel, direction: MovePosition) {
        coroutineScope.launch {
            postController.moveTo(post.id, direction)
        }
    }

    private fun observeUpdates() {
        postController.updateMetadataFlow.let { flow ->
            coroutineScope.launch {
                flow.collect { metadataEntity ->
                    runLater {
                        val postModel = items.items.find { it.id == metadataEntity.postIdRef } ?: return@runLater
                        postModel.altTitles = FXCollections.observableArrayList(metadataEntity.data.resolvedNames)
                        postModel.postedBy = metadataEntity.data.postedBy
                    }
                }
            }
        }

        postController.deletedPostsFlow.let { flow ->
            coroutineScope.launch {
                flow.collect { id ->
                    runLater {
                        items.items.removeIf { it.id == id }
                        requestSort()
                        pruneSelection()
                    }
                }
            }
        }

        postController.updatePostsFlow.let { flow ->
            coroutineScope.launch {
                flow.collect { post ->
                    runLater {
                        val postModel = items.items.find { it.id == post.id } ?: return@runLater
                        postModel.status = post.status.name
                        postModel.progressCount = postController.progressCount(
                            post.total, post.done, post.downloaded
                        )
                        postModel.done = post.done
                        postModel.progress = postController.progress(post.total, post.done)
                        postModel.path = post.getDownloadFolder()
                        postModel.folderName = post.folderName
                    }
                }
            }
        }

        postController.queueStateUpdate.let { flow ->
            coroutineScope.launch {
                flow.collect { queueState ->
                    runLater { updateQueueState(queueState) }
                }
            }
        }

        postController.newPostsFlow.let { flow ->
            coroutineScope.launch {
                flow.collect { posts ->
                    runLater {
                        items.addAll(posts)
                        requestSort()
                    }
                }
            }
        }

        coroutineScope.launch {
            GuiEventBus.events.collect {
                when (it) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        val postModelList = postController.findAllPosts()
                        val queueState = postController.getQueueState()
                        runLater {
                            items.addAll(postModelList)
                            requestSort()
                            placeholderState = PostsPlaceholderState.EMPTY
                            updateQueueState(queueState)
                        }
                    }

                    GuiEventBus.ChangingSession -> runLater {
                        placeholderState = PostsPlaceholderState.LOADING
                        items.clear()
                        selectedPosts.clear()
                        activePost = null
                    }

                    is GuiEventBus.RemoteError -> runLater {
                        items.clear()
                        selectedPosts.clear()
                        activePost = null
                        placeholderState = PostsPlaceholderState.CONNECTION_FAILURE
                    }

                    else -> {}
                }
            }
        }
    }

    private fun updateQueueState(queueState: QueueState) {
        var orderChanged = false
        items.items.forEach { post ->
            val rank = queueState.rank.find { it.postEntityId == post.id }
            val newOrder = rank?.rank ?: -1L
            if (post.order != newOrder) {
                post.order = newOrder
                orderChanged = true
            }
        }
        if (orderChanged) requestSort()
    }
}
