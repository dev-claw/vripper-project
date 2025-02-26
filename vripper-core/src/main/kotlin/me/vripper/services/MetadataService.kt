package me.vripper.services

import me.vripper.tasks.FetchMetadataTask
import me.vripper.utilities.taskRunner

internal class MetadataService(
    private val dataTransaction: DataTransaction,
    private val settingsService: SettingsService,
) {

    fun init() {
        if (!settingsService.settings.viperSettings.fetchMetadata) {
            return
        }
        dataTransaction.findAllPosts().filter { dataTransaction.findMetadataByPostId(it.postId).isEmpty }
            .map { it.postId }.forEach(::fetchMetadata)
    }

    fun fetchMetadata(postId: Long) {
        if (!settingsService.settings.viperSettings.fetchMetadata) {
            return
        }
        taskRunner.submit(
            FetchMetadataTask(
                postId
            )
        )
    }
}