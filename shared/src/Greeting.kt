package dev.justinsmith.interval

class Greeting(private val platform: Platform) {
    fun greet(): String = "Hello, ${platform.name}!"
}
