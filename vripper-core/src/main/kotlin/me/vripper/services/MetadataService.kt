package me.vripper.services

import me.vripper.entities.PostEntity
import me.vripper.tasks.FetchMetadataTask

internal class MetadataService(
    private val dataAccessService: DataAccessService,
    private val settingsService: SettingsService,
    private val taskService: TaskService,
) {

    fun fetchExisting() {
        if (!settingsService.settings.viperSettings.fetchMetadata) {
            return
        }
        dataAccessService.findAllPosts().filter { dataAccessService.findMetadataByPostEntityId(it.id).isEmpty }
            .map { it }.forEach(::fetchMetadata)
    }

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
