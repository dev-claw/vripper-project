package me.vripper.gui.components

enum class ThreadQuickFilter(val label: String) {
    ALL("All"),
    WITH_POSTS("With posts"),
    EMPTY("Empty"),
}

enum class TaskQuickFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    SUCCESS("Success"),
    ISSUES("Issues"),
}

enum class TaskGroupMode(val label: String) {
    NONE("None"),
    CATEGORY("Category"),
    POST("Post"),
}

enum class LogQuickFilter(val label: String) {
    ALL("All"),
    ERRORS("Errors"),
    WARNINGS("Warnings"),
    INFO("Info"),
    DEBUG("Debug"),
}
