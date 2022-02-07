package kotlinx.atomicfu.plugin.gradle.internal

import java.io.*
import kotlin.test.*

fun File.checkExists() {
    assertTrue(exists(), "File does not exist: $canonicalPath")
}

fun File.filesFrom(relative: String) = resolve(relative)
    .readLines().asSequence().flatMap { listFiles(it) }.toHashSet()

fun listFiles(dir: String): Sequence<File> = File(dir).walk().filter { it.isFile }