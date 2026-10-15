package dev.justinsmith.interval

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
