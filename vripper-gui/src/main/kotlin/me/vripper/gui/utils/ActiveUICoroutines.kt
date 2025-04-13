package me.vripper.gui.utils

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ActiveUICoroutines {

    private val mutex = Mutex()

    private val posts: MutableList<Job> = mutableListOf()
    private val actionBar: MutableList<Job> = mutableListOf()
    private val images: MutableList<Job> = mutableListOf()
    private val logs: MutableList<Job> = mutableListOf()
    private val menuBar: MutableList<Job> = mutableListOf()
    private val postInfo: MutableList<Job> = mutableListOf()
    private val statusBar: MutableList<Job> = mutableListOf()
    private val threads: MutableList<Job> = mutableListOf()

    suspend fun cancelPosts() {
        mutex.withLock {
            posts.forEach { it.cancelAndJoin() }
            posts.clear()
        }
    }

    suspend fun addToPosts(job: Job) {
        mutex.withLock {
            posts.add(job)
        }
    }

    suspend fun removeFromPosts(job: Job) {
        mutex.withLock {
            job.cancel()
            posts.remove(job)
        }
    }

    suspend fun cancelActionBar() {
        mutex.withLock {
            actionBar.forEach { it.cancelAndJoin() }
            actionBar.clear()
        }
    }

    suspend fun addToActionBar(job: Job) {
        mutex.withLock {
            actionBar.add(job)
        }
    }

    suspend fun removeFromActionBar(job: Job) {
        mutex.withLock {
            job.cancel()
            actionBar.remove(job)
        }
    }

    suspend fun cancelImages() {
        mutex.withLock {
            images.forEach { it.cancelAndJoin() }
            images.clear()
        }
    }

    suspend fun addToImages(job: Job) {
        mutex.withLock {
            images.add(job)
        }
    }

    suspend fun removeFromImages(job: Job) {
        mutex.withLock {
            job.cancel()
            images.remove(job)
        }
    }

    suspend fun cancelLog() {
        mutex.withLock {
            logs.forEach { it.cancelAndJoin() }
            logs.clear()
        }
    }

    suspend fun addToLog(job: Job) {
        mutex.withLock {
            logs.add(job)
        }
    }

    suspend fun removeFromLog(job: Job) {
        mutex.withLock {
            job.cancel()
            logs.remove(job)
        }
    }

    suspend fun cancelMenuBar() {
        mutex.withLock {
            menuBar.forEach { it.cancelAndJoin() }
            menuBar.clear()
        }
    }

    suspend fun addToMenuBar(job: Job) {
        mutex.withLock {
            menuBar.add(job)
        }
    }

    suspend fun removeFromMenuBar(job: Job) {
        mutex.withLock {
            job.cancel()
            menuBar.remove(job)
        }
    }

    suspend fun cancelPostInfo() {
        mutex.withLock {
            postInfo.forEach { it.cancelAndJoin() }
            postInfo.clear()
        }
    }

    suspend fun addToPostInfo(job: Job) {
        mutex.withLock {
            postInfo.add(job)
        }
    }

    suspend fun removeFromPostInfo(job: Job) {
        mutex.withLock {
            job.cancel()
            postInfo.remove(job)
        }
    }

    suspend fun cancelStatusBar() {
        mutex.withLock {
            statusBar.forEach { it.cancelAndJoin() }
            statusBar.clear()
        }
    }

    suspend fun addToStatusBar(job: Job) {
        mutex.withLock {
            statusBar.add(job)
        }
    }

    suspend fun removeFromStatusBar(job: Job) {
        mutex.withLock {
            job.cancel()
            statusBar.remove(job)
        }
    }

    suspend fun cancelThreads() {
        mutex.withLock {
            threads.forEach { it.cancelAndJoin() }
            threads.clear()
        }
    }

    suspend fun addToThreads(job: Job) {
        mutex.withLock {
            threads.add(job)
        }
    }

    suspend fun removeFromThreads(job: Job) {
        mutex.withLock {
            job.cancel()
            threads.remove(job)
        }
    }

    suspend fun all() = mutex.withLock {
        listOf(
            posts,
            actionBar,
            logs,
            menuBar,
            postInfo,
            statusBar,
            threads
        ).flatten()
    }
}