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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.vripper.data.Post
import me.vripper.data.QueueState
import me.vripper.session.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsScreen(
    session: SessionState.Connected,
    controller: PostsController,
    onOpenPost: (Post) -> Unit,
    onDisconnect: () -> Unit,
) {
    val posts by controller.posts.collectAsState()
    val queue by controller.queueState.collectAsState()
    var showAddLinks by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val visiblePosts = remember(posts, controller.query, queue.ranks) {
        val term = controller.query.trim()
        val filtered = if (term.isBlank()) {
            posts
        } else {
            posts.filter { post ->
                post.title.contains(term, ignoreCase = true) ||
                    post.threadTitle.contains(term, ignoreCase = true) ||
                    post.forum.contains(term, ignoreCase = true) ||
                    post.hosts.any { it.contains(term, ignoreCase = true) }
            }
        }
        filtered.sortedWith(
            compareBy<Post> { queue.ranks[it.id] ?: Long.MAX_VALUE }
                .thenByDescending { it.addedOn }
                .thenByDescending { it.id },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            colors = vripperTopBarColors(),
            title = {
                Column {
                    Text(
                        text = "Posts",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = sessionSubtitle(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { showAddLinks = true }) {
                    Icon(VripperIcons.Add, contentDescription = "Add links")
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(VripperIcons.More, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Start all") },
                            leadingIcon = { Icon(VripperIcons.Play, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                controller.startAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Stop all") },
                            leadingIcon = { Icon(VripperIcons.Stop, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                controller.stopAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(VripperIcons.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                controller.refresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear finished") },
                            leadingIcon = { Icon(VripperIcons.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showClearConfirm = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            leadingIcon = { Icon(VripperIcons.Close, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onDisconnect()
                            },
                        )
                    }
                }
            },
        )

        if (controller.isBusy || controller.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            QueueSummary(queue = queue, posts = posts.size)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = controller.query,
                onValueChange = controller::onQueryChange,
                label = { Text("Search posts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            when {
                controller.isLoading && posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                posts.isEmpty() && controller.error != null -> {
                    EmptyState(
                        message = controller.error ?: "Unable to load posts",
                        actionLabel = "Retry",
                        onAction = controller::refresh,
                        icon = VripperIcons.Warning,
                    )
                }

                posts.isEmpty() -> {
                    EmptyState(
                        message = "No posts yet.\nTap + to add thread links.",
                        icon = VripperIcons.Posts,
                    )
                }

                visiblePosts.isEmpty() -> {
                    EmptyState(
                        message = "No posts match \"${controller.query.trim()}\"",
                        icon = VripperIcons.Search,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val error = controller.error
                        if (error != null) {
                            item {
                                ErrorBanner(message = error, onRetry = controller::refresh)
                            }
                        }
                        items(visiblePosts, key = { it.id }) { post ->
                            PostCard(post = post, onClick = { onOpenPost(post) })
                        }
                    }
                }
            }
        }
    }

    if (showAddLinks) {
        AddLinksDialog(
            onDismiss = { showAddLinks = false },
            onScan = { links ->
                showAddLinks = false
                controller.scanLinks(links)
            },
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear finished posts",
            message = "Remove all finished posts from the server?",
            confirmLabel = "Clear",
            onConfirm = controller::clearCompleted,
            onDismiss = { showClearConfirm = false },
        )
    }
}

private fun sessionSubtitle(session: SessionState.Connected): String = buildString {
    append(session.user.ifBlank { "VRipper" })
    if (session.version.isNotBlank()) {
        append(" · v").append(session.version)
    }
}

@Composable
private fun AddLinksDialog(
    onDismiss: () -> Unit,
    onScan: (String) -> Unit,
) {
    var links by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add thread links") },
        text = {
            OutlinedTextField(
                value = links,
                onValueChange = { links = it },
                label = { Text("One link per line") },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onScan(links) },
                enabled = links.isNotBlank(),
            ) { Text("Scan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun QueueSummary(queue: QueueState, posts: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryTile(label = "Running", value = queue.running.toString(), modifier = Modifier.weight(1f))
        SummaryTile(label = "Remaining", value = queue.remaining.toString(), modifier = Modifier.weight(1f))
        SummaryTile(label = "Posts", value = posts.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PostCard(post: Post, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.title.ifBlank { "Untitled post" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (post.threadTitle.isNotBlank()) {
                        Text(
                            text = post.threadTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(post.status)
            }

            if (post.total > 0) {
                LinearProgressIndicator(
                    progress = { post.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${post.done} / ${post.total} files",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatBytes(post.downloaded)} / ${formatBytes(post.size)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val details = buildList {
                if (post.forum.isNotBlank()) add(post.forum)
                if (post.hosts.isNotEmpty()) add(post.hosts.joinToString(", "))
                if (post.postedBy.isNotBlank()) add("by ${post.postedBy}")
                val addedOn = formatAddedOn(post.addedOn)
                if (addedOn.isNotBlank()) add(addedOn)
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
