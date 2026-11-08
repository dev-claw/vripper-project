package me.vripper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.vripper.data.ForumThread
import me.vripper.data.ThreadPostRef
import me.vripper.data.ThreadSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadsScreen(
    controller: ThreadsController,
    onOpenPosts: () -> Unit,
) {
    val threads by controller.threads.collectAsState()
    var selectedThread by remember { mutableStateOf<ForumThread?>(null) }
    var threadToRemove by remember { mutableStateOf<ForumThread?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val current = selectedThread
    if (current != null) {
        ThreadPostsScreen(
            controller = controller,
            thread = current,
            onBack = { selectedThread = null },
            onDownloaded = {
                selectedThread = null
                onOpenPosts()
            },
        )
        return
    }

    val visible = remember(threads, controller.query) {
        val term = controller.query.trim()
        if (term.isBlank()) {
            threads
        } else {
            threads.filter {
                it.title.contains(term, ignoreCase = true) ||
                    it.link.contains(term, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            colors = vripperTopBarColors(),
            title = {
                Column {
                    Text("Threads", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${threads.size} cached threads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = controller::refresh,
                    enabled = !controller.isLoading && !controller.isBusy,
                ) {
                    Icon(VripperIcons.Refresh, contentDescription = "Refresh")
                }
                IconButton(
                    onClick = { showClearConfirm = true },
                    enabled = threads.isNotEmpty() && !controller.isBusy,
                ) {
                    Icon(VripperIcons.Delete, contentDescription = "Clear threads")
                }
            },
        )

        if (controller.isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = controller.query,
                onValueChange = controller::onQueryChange,
                label = { Text("Search threads") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            when {
                controller.isLoading && threads.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                threads.isEmpty() && controller.error != null -> {
                    EmptyState(
                        message = controller.error ?: "Unable to load threads",
                        actionLabel = "Retry",
                        onAction = controller::refresh,
                        icon = VripperIcons.Warning,
                    )
                }

                threads.isEmpty() -> {
                    EmptyState(
                        message = "No cached threads yet.\nAdd thread links from the Posts tab.",
                        icon = VripperIcons.Threads,
                    )
                }

                visible.isEmpty() -> {
                    EmptyState(
                        message = "No threads match \"${controller.query.trim()}\"",
                        icon = VripperIcons.Search,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        controller.error?.let { error ->
                            item { ErrorBanner(message = error, onRetry = controller::refresh) }
                        }
                        items(visible, key = { it.id }) { thread ->
                            ThreadCard(
                                thread = thread,
                                onClick = { selectedThread = thread },
                                onRemove = { threadToRemove = thread },
                            )
                        }
                    }
                }
            }
        }
    }

    threadToRemove?.let { thread ->
        ConfirmDialog(
            title = "Remove thread",
            message = "Remove \"${thread.title}\" from the cached threads?",
            confirmLabel = "Remove",
            onConfirm = { controller.remove(thread.threadId) },
            onDismiss = { threadToRemove = null },
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear threads",
            message = "Remove all cached threads from the server?",
            confirmLabel = "Clear",
            onConfirm = controller::clear,
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun ThreadCard(
    thread: ForumThread,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = thread.title.ifBlank { "Untitled thread" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${thread.total} posts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (thread.link.isNotBlank()) {
                Text(
                    text = thread.link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onClick) { Text("Select posts") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadPostsScreen(
    controller: ThreadsController,
    thread: ForumThread,
    onBack: () -> Unit,
    onDownloaded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selections by remember(thread.threadId) { mutableStateOf<List<ThreadSelection>>(emptyList()) }
    var selected by remember(thread.threadId) { mutableStateOf<Set<Long>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isDownloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(thread.threadId, reloadKey) {
        isLoading = true
        error = null
        try {
            selections = controller.grab(thread.threadId)
            selected = emptySet()
        } catch (t: Throwable) {
            error = t.userMessage("Unable to load thread posts")
        } finally {
            isLoading = false
        }
    }

    val visible = remember(selections, query) {
        val term = query.trim()
        if (term.isBlank()) {
            selections
        } else {
            selections.filter {
                it.title.contains(term, ignoreCase = true) ||
                    it.hosts.contains(term, ignoreCase = true) ||
                    it.number.toString() == term
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            colors = vripperTopBarColors(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(VripperIcons.Back, contentDescription = "Back")
                }
            },
            title = {
                Text(
                    text = thread.title.ifBlank { "Thread" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isDownloading = true
                            try {
                                controller.download(
                                    selections
                                        .filter { it.postId in selected }
                                        .map { ThreadPostRef(it.threadId, it.postId) },
                                )
                                onDownloaded()
                            } catch (t: Throwable) {
                                error = t.userMessage("Unable to queue the selected posts")
                            } finally {
                                isDownloading = false
                            }
                        }
                    },
                    enabled = selected.isNotEmpty() && !isDownloading,
                ) {
                    Icon(VripperIcons.Tasks, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isDownloading) "Queueing…" else "Download")
                }
            },
        )

        if (isLoading || isDownloading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search posts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selected.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        selected = if (visible.isNotEmpty() && visible.all { it.postId in selected }) {
                            emptySet()
                        } else {
                            visible.map { it.postId }.toSet()
                        }
                    },
                    enabled = visible.isNotEmpty(),
                ) {
                    Text(
                        if (visible.isNotEmpty() && visible.all { it.postId in selected }) {
                            "Clear"
                        } else {
                            "Select all"
                        },
                    )
                }
            }

            when {
                isLoading && selections.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                error != null && selections.isEmpty() -> {
                    EmptyState(
                        message = error ?: "Unable to load thread posts",
                        actionLabel = "Retry",
                        onAction = { reloadKey++ },
                        icon = VripperIcons.Warning,
                    )
                }

                selections.isEmpty() -> {
                    EmptyState(
                        message = "This thread has no posts.",
                        icon = VripperIcons.Threads,
                    )
                }

                visible.isEmpty() -> {
                    EmptyState(
                        message = "No posts match \"${query.trim()}\"",
                        icon = VripperIcons.Search,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        error?.let { message ->
                            item {
                                ErrorBanner(
                                    message = message,
                                    onRetry = { reloadKey++ },
                                )
                            }
                        }
                        items(visible, key = { "${it.threadId}:${it.postId}" }) { item ->
                            ThreadPostRow(
                                item = item,
                                checked = item.postId in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) {
                                        selected + item.postId
                                    } else {
                                        selected - item.postId
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadPostRow(
    item: ThreadSelection,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${item.number} ${item.title}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = buildList {
                    if (item.hosts.isNotBlank()) add(item.hosts)
                    if (item.imageCount > 0) add("${item.imageCount} images")
                    if (item.forum.isNotBlank()) add(item.forum)
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
