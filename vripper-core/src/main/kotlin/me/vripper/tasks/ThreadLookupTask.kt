package me.vripper.tasks

import me.vripper.entities.ThreadEntity
import me.vripper.model.Settings
import me.vripper.model.ThreadPostId
import me.vripper.services.DataAccessService
import me.vripper.services.SettingsService
import me.vripper.services.ThreadCacheService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.taskRunner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class ThreadLookupTask(private val threadId: Long, private val settings: Settings) : KoinComponent, Runnable {
    private val log by LoggerDelegate()
    private val dataAccessService by inject<DataAccessService>()
    private val settingsService by inject<SettingsService>()
    private val threadCacheService by inject<ThreadCacheService>()
    private val link: String = "${settingsService.settings.viperSettings.host}/threads/$threadId"

    override fun run() {
        try {
            Tasks.increment()
            if (dataAccessService.findThreadByThreadId(threadId).isEmpty) {
                val threadLookupResult = threadCacheService[threadId]
                if (threadLookupResult.error.isNotBlank()) {
                    log.error("Error loading $link: ${threadLookupResult.error}")
                    return
                }
                if (threadLookupResult.postItemList.isEmpty()) {
                    log.error("Nothing found for $link")
                    return
                }

                if (threadLookupResult.postItemList.size <= settings.downloadSettings.autoQueueThreshold) {
                    taskRunner.submit(
                        AddPostTask(threadLookupResult.postItemList.map {
                            ThreadPostId(
                                it.threadId, it.postId
                            )
                        })
                    )
                } else {
                    dataAccessService.save(
                            ThreadEntity(
                                title = threadLookupResult.title,
                                link = link,
                                threadId = threadId,
                                total = threadLookupResult.postItemList.size
                            )
                        )
                }
            }
        } catch (e: Exception) {
            log.error("Error when processing $link", e)
        } finally {
            Tasks.decrement()
        }
    }
}