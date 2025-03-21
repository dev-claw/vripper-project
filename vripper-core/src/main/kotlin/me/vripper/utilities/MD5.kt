package me.vripper.utilities

import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

fun md5Hex(data: String): String {
    val md: MessageDigest = MessageDigest.getInstance("MD5")
    md.update(data.toByteArray())
    return DatatypeConverter.printHexBinary(md.digest()).lowercase()
}