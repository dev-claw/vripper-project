package me.vripper.ui

import kotlin.math.ln
import kotlin.math.pow

private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, UNITS.lastIndex)
    if (exponent == 0) return "$bytes B"
    val value = (bytes / 1024.0.pow(exponent) * 10).toLong() / 10.0
    val text = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return "$text ${UNITS[exponent]}"
}

fun formatStatus(status: String): String =
    status.lowercase()
        .replace('_', ' ')
        .replaceFirstChar { it.uppercaseChar() }
        .ifBlank { "Unknown" }

fun formatAddedOn(value: String): String {
    if (value.isBlank()) return ""
    val dateTime = value.replace('T', ' ')
    return if (dateTime.length >= 16) dateTime.take(16) else dateTime
}
