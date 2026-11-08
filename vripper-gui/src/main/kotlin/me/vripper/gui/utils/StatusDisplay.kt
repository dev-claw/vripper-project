package me.vripper.gui.utils

import me.vripper.entities.Status

fun Status.displayLabel(): String = when (this) {
    Status.STOPPED -> "Paused"
    else -> stringValue
}

fun parseStatus(value: String): Status? = Status.entries.firstOrNull {
    it.name.equals(value, ignoreCase = true) || it.stringValue.equals(value, ignoreCase = true)
}

fun String.displayStatusLabel(): String = parseStatus(this)?.displayLabel() ?: this

fun String.matchesStatus(status: Status): Boolean = parseStatus(this) == status

fun String.displayActionState(): String = when (uppercase()) {
    "", "NONE" -> "None"
    "AWAITING_REVIEW" -> "Awaiting review"
    "READY" -> "Ready"
    "RUNNING" -> "Running"
    "DONE" -> "Done"
    "FAILED" -> "Failed"
    "SKIPPED" -> "Skipped"
    else -> lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
