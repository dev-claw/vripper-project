package me.vripper.utilities

import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest

fun Throwable.formatToString(): String {
    return StringWriter().use { stringWriter ->
        PrintWriter(stringWriter).use { printWriter ->
            this.printStackTrace(printWriter)
            printWriter.flush()
            stringWriter.flush()
            stringWriter.toString()
        }
    }
}

fun Long.formatSI(): String {
    if (this < 0) {
        return "? Bytes"
    }
    return humanReadableByteCount(this, false)
}

private fun humanReadableByteCount(bytes: Long, si: Boolean): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return "$bytes Bytes"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString() + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

fun String.hash256(): String {
    val bytes = this.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

fun String.extractBaseUrl(): String {
    return this.replaceFirst(Regex("(https?://[^/]+).*"), "$1")
}