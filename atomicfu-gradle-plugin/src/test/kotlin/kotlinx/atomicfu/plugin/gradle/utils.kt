/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.assertTrue

fun BuildResult.checkOutcomes(expected: TaskOutcome, vararg tasks: String) {
    val unexpectedOutcomes = tasks
            .map { it to task(it)?.outcome }
            .filter { (_, outcome) -> outcome != expected }
    if (unexpectedOutcomes.isNotEmpty()) {
        throw AssertionError("Unexpected outcomes for tasks." +
                "\nExpected: $expected." +
                "\nGot:" +
                "\n${unexpectedOutcomes.joinToString("\n") { (task, outcome) -> "* $task -> $outcome" }}")

    }
}

fun File.checkExists() {
    assertTrue(exists(), "File does not exist: $canonicalPath")
}

fun File.modify(fn: (String) -> String) {
    writeText(fn(readText()))
}

fun String.checkedReplace(oldValue: String, newValue: String, ignoreCase: Boolean = false): String {
    check(contains(oldValue, ignoreCase)) { "String must contain '$oldValue'" }
    return replace(oldValue, newValue, ignoreCase)
}