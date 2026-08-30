package me.vripper.entities

enum class Status(val stringValue: String) {
    PENDING("Pending"),
    DOWNLOADING("Downloading"),
    FINISHED("Finished"),
    ERROR("Error"),
    STOPPED("Stopped"),
    ON_HOLD("On Hold"),
    AUTOMATION("Automation"),
}