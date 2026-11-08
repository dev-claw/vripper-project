package me.vripper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.vripper.data.LogEntry

private val LEVELS = listOf<String?>(null, "DEBUG", "INFO", "WARN", "ERROR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(controller: LogsController) {
    val logs by controller.logs.collectAsState()
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    val visible = remember(logs, controller.query, controller.levelFilter) {
        val term = controller.query.trim()
        logs.asReversed().filter { entry ->
            val matchesLevel = controller.levelFilter == null ||
                entry.level.equals(controller.levelFilter, ignoreCase = true)
            val matchesTerm = term.isBlank() ||
                entry.message.contains(term, ignoreCase = true) ||
                entry.loggerName.contains(term, ignoreCase = true) ||
                entry.threadName.contains(term, ignoreCase = true) ||
                entry.level.contains(term, ignoreCase = true)
            matchesLevel && matchesTerm
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            colors = vripperTopBarColors(),
            title = {
                Column {
                    Text("Logs", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${logs.size} events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = controller::clear,
                    enabled = logs.isNotEmpty(),
                ) {
                    Icon(VripperIcons.Delete, contentDescription = "Clear logs")
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = controller.query,
                onValueChange = controller::onQueryChange,
                label = { Text("Search logs") },
                leadingIcon = { Icon(VripperIcons.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LEVELS.forEach { level ->
                    FilterChip(
                        selected = controller.levelFilter == level,
                        onClick = { controller.onLevelFilterChange(level) },
                        label = { Text(level ?: "All") },
                    )
                }
            }
            controller.error?.let { error ->
                ErrorBanner(message = error, onRetry = controller::retryInit)
            }
            Spacer(Modifier.height(8.dp))

            if (visible.isEmpty()) {
                EmptyState(
                    message = if (logs.isEmpty()) {
                        "Waiting for log events from the server."
                    } else {
                        "No logs match the current filters."
                    },
                    icon = if (logs.isEmpty()) VripperIcons.Logs else VripperIcons.Search,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.uid }) { entry ->
                        LogCard(
                            entry = entry,
                            expanded = expanded[entry.uid] == true,
                            onToggle = {
                                expanded[entry.uid] = expanded[entry.uid] != true
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(entry: LogEntry, expanded: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAddedOn(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(entry.level)
            }
            Text(
                text = entry.message.ifBlank { "(empty log message)" },
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded) {
                if (entry.loggerName.isNotBlank()) {
                    Text(
                        text = "Logger: ${entry.loggerName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.threadName.isNotBlank()) {
                    Text(
                        text = "Thread: ${entry.threadName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.throwable.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.throwable,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text(
                    text = "Tap for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
