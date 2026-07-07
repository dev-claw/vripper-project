package me.vripper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.vripper.data.Image
import me.vripper.data.Post
import me.vripper.session.SessionState

private data class ViewerTarget(val images: List<Image>, val index: Int)

private enum class MainTab(val label: String, val icon: ImageVector) {
    POSTS("Posts", VripperIcons.Posts),
    THREADS("Threads", VripperIcons.Threads),
    TASKS("Tasks", VripperIcons.Tasks),
    LOGS("Logs", VripperIcons.Logs),
    SETTINGS("Settings", VripperIcons.Settings),
}

@Composable
fun MainScreen(
    session: SessionState.Connected,
    onDisconnect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val postsController = remember(session.api) { PostsController(session.api, scope) }
    val threadsController = remember(session.api) { ThreadsController(session.api, scope) }
    val tasksController = remember(session.api) { TasksController(session.api, scope) }
    val logsController = remember(session.api) { LogsController(session.api, scope) }
    val settingsController = remember(session.api) { SettingsController(session.api, scope) }
    val statsController = remember(session.api) {
        SessionStatsController(session.api, scope, session.user)
    }

    var tab by remember { mutableStateOf(MainTab.POSTS) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var viewer by remember { mutableStateOf<ViewerTarget?>(null) }

    val target = viewer
    if (target != null) {
        ImageViewerScreen(
            images = target.images,
            initialIndex = target.index,
            api = session.api,
            onBack = { viewer = null },
        )
        return
    }

    val post = selectedPost
    if (post != null) {
        PostDetailScreen(
            api = session.api,
            initialPost = post,
            onBack = { selectedPost = null },
            onOpenImages = { images, index -> viewer = ViewerTarget(images, index) },
        )
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                StatusFooter(session = session, stats = statsController)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    MainTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = {
                                Icon(
                                    imageVector = entry.icon,
                                    contentDescription = entry.label,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                MainTab.POSTS -> PostsScreen(
                    session = session,
                    controller = postsController,
                    onOpenPost = { selectedPost = it },
                    onDisconnect = onDisconnect,
                )

                MainTab.THREADS -> ThreadsScreen(
                    controller = threadsController,
                    onOpenPosts = { tab = MainTab.POSTS },
                )

                MainTab.TASKS -> TasksScreen(controller = tasksController)

                MainTab.LOGS -> LogsScreen(controller = logsController)

                MainTab.SETTINGS -> SettingsScreen(controller = settingsController)
            }
        }
    }
}

@Composable
private fun StatusFooter(
    session: SessionState.Connected,
    stats: SessionStatsController,
) {
    val user by stats.user.collectAsState()
    val speed by stats.downloadSpeed.collectAsState()
    val errors by stats.errorCount.collectAsState()
    val tasksRunning by stats.tasksRunning.collectAsState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
            )
            Text(
                text = user.ifBlank { session.config.displayName },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tasksRunning) {
                Icon(
                    imageVector = VripperIcons.Tasks,
                    contentDescription = "Working",
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = "${formatBytes(speed)}/s",
                style = MaterialTheme.typography.labelSmall,
            )
            if (errors > 0) {
                Icon(
                    imageVector = VripperIcons.Warning,
                    contentDescription = "$errors errors",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = errors.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
