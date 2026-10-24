package me.vripper.services

import me.vripper.entities.PostEntity
import me.vripper.tasks.FetchMetadataTask

internal class MetadataService(
    private val settingsService: SettingsService,
    private val taskService: TaskService,
) {
    fun fetchMetadata(post: PostEntity) {
        if (!settingsService.settings.viperSettings.fetchMetadata) {
            return
        }
        taskService.submit(
            FetchMetadataTask(
                post
            )
        )
    }
}
