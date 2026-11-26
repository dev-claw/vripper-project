package me.vripper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import me.vripper.api.EndpointApi
import me.vripper.data.Image
import me.vripper.data.MovePosition
import me.vripper.data.Post

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    api: EndpointApi,
    initialPost: Post,
    onBack: () -> Unit,
    onOpenImages: (List<Image>, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(api, initialPost.id) {
        PostDetailController(api, initialPost.id, initialPost, scope)
    }
    val post = controller.post.collectAsState().value ?: initialPost
    val images by controller.images.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = vripperTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(VripperIcons.Back, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = post.title.ifBlank { "Post" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(
                        onClick = { if (post.isRunning) controller.stop() else controller.start() },
                        enabled = !controller.isBusy,
                    ) {
                        Icon(
                            imageVector = if (post.isRunning) VripperIcons.Stop else VripperIcons.Play,
                            contentDescription = if (post.isRunning) "Stop" else "Start",
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(VripperIcons.More, contentDescription = "More actions")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    showMenu = false
                                    showRename = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Auto rename") },
                                onClick = {
                                    showMenu = false
                                    controller.renameToFirst()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to top") },
                                onClick = {
                                    showMenu = false
                                    controller.move(MovePosition.TOP)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                onClick = {
                                    showMenu = false
                                    controller.move(MovePosition.UP)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                onClick = {
                                    showMenu = false
                                    controller.move(MovePosition.DOWN)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to bottom") },
                                onClick = {
                                    showMenu = false
                                    controller.move(MovePosition.BOTTOM)
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
                                text = { Text("Delete") },
                                leadingIcon = { Icon(VripperIcons.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showRemove = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (controller.isBusy || controller.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            controller.error?.let { error ->
                ErrorBanner(message = error, onRetry = controller::dismissError)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { PostOverviewCard(post) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Images (${images.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val finished = images.count { it.status.equals("FINISHED", ignoreCase = true) }
                        Text(
                            text = "$finished finished",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (controller.isLoading && images.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (images.isEmpty()) {
                    item {
                        Text(
                            text = "No images have been resolved for this post yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(images, key = { it.id }) { image ->
                        ImageRow(
                            image = image,
                            onClick = {
                                val viewable = images.filter {
                                    it.status.equals("FINISHED", ignoreCase = true)
                                }
                                val index = viewable.indexOfFirst { it.id == image.id }
                                if (index >= 0) onOpenImages(viewable, index)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        RenamePostDialog(
            post = post,
            onDismiss = { showRename = false },
            onRename = { name ->
                showRename = false
                controller.rename(name)
            },
        )
    }

    if (showRemove) {
        ConfirmDialog(
            title = "Remove post",
            message = "Remove \"${post.title.ifBlank { post.folderName }}\" and its files from the server?",
            confirmLabel = "Remove",
            onConfirm = { controller.remove(onRemoved = onBack) },
            onDismiss = { showRemove = false },
        )
    }
}

@Composable
private fun PostOverviewCard(post: Post) {
    SectionCard(title = "Overview") {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = post.title.ifBlank { "Untitled post" },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (post.threadTitle.isNotBlank()) {
                    Text(
                        text = post.threadTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(post.status)
        }

        if (post.total > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow(label = "Forum", value = post.forum)
            InfoRow(label = "Hosts", value = post.hosts.joinToString(", "))
            InfoRow(label = "Posted by", value = post.postedBy)
            InfoRow(label = "Added", value = formatAddedOn(post.addedOn))
            InfoRow(label = "Folder", value = post.folderName)
            InfoRow(label = "Path", value = post.downloadDirectory)
            InfoRow(label = "URL", value = post.url)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ImageRow(image: Image, onClick: () -> Unit) {
    val finished = image.status.equals("FINISHED", ignoreCase = true)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = finished, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemotePreview(
                url = image.thumbUrl,
                contentDescription = image.displayName,
                modifier = Modifier
                    .width(64.dp)
                    .height(64.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = image.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(image.status)
                }
                Text(
                    text = "#${image.index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (image.size > 0) {
                    LinearProgressIndicator(
                        progress = { image.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${formatBytes(image.downloaded)} / ${formatBytes(image.size)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (finished) {
                    Text(
                        text = "Tap to view",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenamePostDialog(
    post: Post,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(post.folderName) { mutableStateOf(post.folderName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = post.resolvedNames.filter { it.isNotBlank() && it != name }.take(3)
                if (suggestions.isNotEmpty()) {
                    Text(
                        text = "Suggestions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    suggestions.forEach { suggestion ->
                        TextButton(onClick = { name = suggestion }) {
                            Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank(),
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
