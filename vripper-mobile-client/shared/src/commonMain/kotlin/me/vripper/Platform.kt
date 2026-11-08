package me.vripper

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform