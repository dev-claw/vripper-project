package me.vripper.utilities

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val taskRunner: ExecutorService = Executors.newFixedThreadPool(6)
val downloadRunner: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()