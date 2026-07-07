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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import me.vripper.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(controller: SettingsController) {
    val settings by controller.settings.collectAsState()
    val proxies by controller.proxies.collectAsState()
    var draft by remember(settings) { mutableStateOf(settings) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            colors = vripperTopBarColors(),
            title = {
                Column {
                    Text("Settings", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Stored on the VRipper server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = controller::refresh,
                    enabled = !controller.isLoading && !controller.isSaving,
                ) {
                    Icon(VripperIcons.Refresh, contentDescription = "Reload")
                }
                TextButton(
                    onClick = { draft?.let(controller::save) },
                    enabled = draft != null && !controller.isSaving,
                ) { Text(if (controller.isSaving) "Saving" else "Save") }
            },
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            controller.error?.let { error ->
                ErrorBanner(message = error)
            }
            controller.savedMessage?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val current = draft
            when {
                current == null && controller.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                current == null -> {
                    EmptyState(
                        message = controller.error ?: "Unable to load settings",
                        actionLabel = "Retry",
                        onAction = controller::refresh,
                        icon = VripperIcons.Warning,
                    )
                }

                else -> {
                    val update: ((Settings) -> Settings) -> Unit = { transform ->
                        draft = draft?.let(transform)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            DownloadSection(current, update)
                        }
                        item {
                            ConnectionSection(current, update)
                        }
                        item {
                            ViperSection(current, update)
                        }
                        item {
                            SystemSection(current, update)
                        }
                        item {
                            SectionCard(title = "Proxies") {
                                if (proxies.isEmpty()) {
                                    Text(
                                        text = "No proxies configured.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    proxies.forEach { proxy ->
                                        Text(
                                            text = proxy,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            SectionCard(title = "Host-specific settings") {
                                if (current.hostSettings.isEmpty()) {
                                    Text(
                                        text = "No host-specific overrides.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    current.hostSettings.forEach { (host, values) ->
                                        Text(
                                            text = host,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        values.forEach { (key, value) ->
                                            Text(
                                                text = "$key = $value",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = { draft?.let(controller::save) },
                                enabled = !controller.isSaving,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (controller.isSaving) "Saving" else "Save settings")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
) {
    SectionCard(title = "Download") {
        SettingsTextField(
            label = "Download path",
            value = settings.download.downloadPath,
            onValueChange = { value ->
                update { it.copy(download = it.download.copy(downloadPath = value)) }
            },
        )
        SettingsSwitch(
            label = "Start downloads automatically",
            checked = settings.download.autoStart,
            onCheckedChange = { value ->
                update { it.copy(download = it.download.copy(autoStart = value)) }
            },
        )
        IntField(
            label = "Auto queue threshold",
            value = settings.download.autoQueueThreshold,
            onValueChange = { value ->
                update { it.copy(download = it.download.copy(autoQueueThreshold = value)) }
            },
        )
        SettingsSwitch(
            label = "Force queue order",
            checked = settings.download.forceOrder,
            onCheckedChange = { value ->
                update { it.copy(download = it.download.copy(forceOrder = value)) }
            },
        )
        SettingsSwitch(
            label = "Forum sub-directories",
            checked = settings.download.forumSubDirectory,
            onCheckedChange = { value ->
                update { it.copy(download = it.download.copy(forumSubDirectory = value)) }
            },
        )
        SettingsSwitch(
            label = "Thread sub-location",
            checked = settings.download.threadSubLocation,
            onCheckedChange = { value ->
                update { it.copy(download = it.download.copy(threadSubLocation = value)) }
            },
        )
        SettingsSwitch(
            label = "Clear completed automatically",
            checked = settings.download.clearCompleted,
            onCheckedChange = { value ->
                update { it.copy(download = it.download.copy(clearCompleted = value)) }
            },
        )
        SettingsSwitch(
            label = "Append post id to folder name",
            checked = settings.download.appendPostId,
            onCheckedChange = { value ->
                update { it.copy(download = it.download.copy(appendPostId = value)) }
            },
        )
    }
}

@Composable
private fun ConnectionSection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
) {
    SectionCard(title = "Connection") {
        IntField(
            label = "Max concurrent per host",
            value = settings.connection.maxConcurrentPerHost,
            onValueChange = { value ->
                update { it.copy(connection = it.connection.copy(maxConcurrentPerHost = value)) }
            },
        )
        IntField(
            label = "Max global concurrent",
            value = settings.connection.maxGlobalConcurrent,
            onValueChange = { value ->
                update { it.copy(connection = it.connection.copy(maxGlobalConcurrent = value)) }
            },
        )
        IntField(
            label = "Timeout (seconds)",
            value = settings.connection.timeout,
            onValueChange = { value ->
                update { it.copy(connection = it.connection.copy(timeout = value)) }
            },
        )
        IntField(
            label = "Max attempts",
            value = settings.connection.maxAttempts,
            onValueChange = { value ->
                update { it.copy(connection = it.connection.copy(maxAttempts = value)) }
            },
        )
    }
}

@Composable
private fun ViperSection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    SectionCard(title = "ViperGirls") {
        SettingsSwitch(
            label = "Login enabled",
            checked = settings.viper.login,
            onCheckedChange = { value ->
                update { it.copy(viper = it.viper.copy(login = value)) }
            },
        )
        SettingsTextField(
            label = "Username",
            value = settings.viper.username,
            onValueChange = { value ->
                update { it.copy(viper = it.viper.copy(username = value)) }
            },
        )
        OutlinedTextField(
            value = settings.viper.password,
            onValueChange = { value ->
                update { it.copy(viper = it.viper.copy(password = value)) }
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { showPassword = !showPassword }) {
            Text(if (showPassword) "Hide password" else "Show password")
        }
        SettingsSwitch(
            label = "Thank posts",
            checked = settings.viper.thanks,
            onCheckedChange = { value ->
                update { it.copy(viper = it.viper.copy(thanks = value)) }
            },
        )
        SettingsTextField(
            label = "Preferred host",
            value = settings.viper.host,
            onValueChange = { value ->
                update { it.copy(viper = it.viper.copy(host = value)) }
            },
        )
        LongField(
            label = "Request limit",
            value = settings.viper.requestLimit,
            onValueChange = { value ->
                update { it.copy(viper = it.viper.copy(requestLimit = value)) }
            },
        )
        SettingsSwitch(
            label = "Fetch metadata",
            checked = settings.viper.fetchMetadata,
            onCheckedChange = { value ->
                update { it.copy(viper = it.viper.copy(fetchMetadata = value)) }
            },
        )
    }
}

@Composable
private fun SystemSection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
) {
    SectionCard(title = "System") {
        SettingsTextField(
            label = "Temporary path",
            value = settings.system.tempPath,
            onValueChange = { value ->
                update { it.copy(system = it.system.copy(tempPath = value)) }
            },
        )
        SettingsSwitch(
            label = "Monitor clipboard for links",
            checked = settings.system.enableClipboardMonitoring,
            onCheckedChange = { value ->
                update { it.copy(system = it.system.copy(enableClipboardMonitoring = value)) }
            },
        )
        IntField(
            label = "Clipboard polling rate (ms)",
            value = settings.system.clipboardPollingRate,
            onValueChange = { value ->
                update { it.copy(system = it.system.copy(clipboardPollingRate = value)) }
            },
        )
        IntField(
            label = "Maximum log events",
            value = settings.system.maxEventLog,
            onValueChange = { value ->
                update { it.copy(system = it.system.copy(maxEventLog = value)) }
            },
        )
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }.take(9)
            text = filtered
            filtered.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LongField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }.take(12)
            text = filtered
            filtered.toLongOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
