package me.vripper.services.download

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

object SharedLock {
    val downloadManagerLock = ReentrantLock()
    val downloadManagerCondition: Condition = downloadManagerLock.newCondition()
}