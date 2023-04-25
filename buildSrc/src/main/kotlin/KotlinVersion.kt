@file:JvmName("KotlinVersion")

import org.gradle.api.*

fun isKotlinVersionAtLeast(kotlinVersion: String, atLeastMajor: Int, atLeastMinor: Int): Boolean {
    val (major, minor) = kotlinVersion
        .split('.')
        .take(2)
        .map { it.toInt() }
    return major == atLeastMajor && minor >= atLeastMinor
}
