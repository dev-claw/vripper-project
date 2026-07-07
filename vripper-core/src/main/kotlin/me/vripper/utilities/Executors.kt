package me.vripper.utilities

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val downloadRunner: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()