package com.example.composeup.utils

import java.util.regex.Pattern
import kotlin.system.measureNanoTime

private val EMAIL_PATTERN: Pattern =
    Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

fun String?.isInvalidEmail(): Boolean {
    return this != null && EMAIL_PATTERN.matcher(this).matches()
}

inline fun <T> T.alsoIf(condition: Boolean, block: (T) -> Unit): T? {
    if (condition) block(this)
    return this
}

fun <T> T?.orDefault(default: T): T = this ?: default

inline fun <T> measureTimeWithResult(block: () -> T): Pair<Long, T> {
    val result: T
    val nanos = measureNanoTime { result = block() }

    return nanos to result
}