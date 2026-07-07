package me.vripper.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import me.vripper.data.DownloadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(controller: TasksController) {
    val tasks by controller.tasks.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            colors = vripperTopBarColors(),
            title = {
                Column {
                    Text("Tasks", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${tasks.size} tracked tasks",
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
                    enabled = tasks.isNotEmpty() && !controller.isBusy,
                ) {
                    Icon(VripperIcons.Delete, contentDescription = "Clear tasks")
                }
            },
        )

        if (controller.isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            when {
                controller.isLoading && tasks.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                tasks.isEmpty() && controller.error != null -> {
                    EmptyState(
                        message = controller.error ?: "Unable to load tasks",
                        actionLabel = "Retry",
                        onAction = controller::refresh,
                        icon = VripperIcons.Warning,
                    )
                }

                tasks.isEmpty() -> {
                    EmptyState(
                        message = "No background tasks",
                        icon = VripperIcons.Tasks,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        controller.error?.let { error ->
                            item { ErrorBanner(message = error, onRetry = controller::refresh) }
                        }
                        items(tasks, key = { it.id }) { task -> TaskCard(task) }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear tasks",
            message = "Remove all tracked tasks from the server?",
            confirmLabel = "Clear",
            onConfirm = controller::clear,
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun TaskCard(task: DownloadTask) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.name.ifBlank { task.category },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(task.status)
            }
            Text(
                text = task.category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (task.result.isNotBlank()) {
                Text(
                    text = task.result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val updated = formatAddedOn(task.updatedAt)
            if (updated.isNotBlank()) {
                Text(
                    text = "Updated $updated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
