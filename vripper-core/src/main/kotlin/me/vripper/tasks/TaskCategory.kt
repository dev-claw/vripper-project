package me.vripper.tasks

enum class TaskCategory(private val str: String) {
    METADATA("Metadata"),
    THANKS("Thanks"),
    QUEUE("Queue"),
    RENAME("Rename"),
    SCAN("Scan"),
    THREAD("Thread"),
}