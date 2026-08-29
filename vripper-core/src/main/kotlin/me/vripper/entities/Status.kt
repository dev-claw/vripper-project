package me.vripper.entities

enum class Status(val stringValue: String) {
    ON_HOLD("On Hold"),
    PENDING("Pending"),
    DOWNLOADING("Downloading"),
    FINISHED("Finished"),
    ERROR("Error"),
    STOPPED("Stopped")
}