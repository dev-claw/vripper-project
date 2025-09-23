package me.vripper.services

import me.vripper.entities.PostEntity
import me.vripper.tasks.FetchMetadataTask
import me.vripper.utilities.taskRunner

internal class MetadataService(
    private val dataAccessService: DataAccessService,
    private val settingsService: SettingsService,
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
        taskRunner.submit(
            FetchMetadataTask(
                post
            )
        )
    }
}