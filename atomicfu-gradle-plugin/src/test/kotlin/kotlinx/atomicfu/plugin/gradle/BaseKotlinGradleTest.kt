/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import org.gradle.internal.impldep.com.google.common.io.Files
import org.junit.After
import org.junit.Before
import java.io.File

abstract class BaseKotlinGradleTest {
    private lateinit var workingDir: File

    fun project(name: String, suffix: String = "", fn: Project.() -> Unit) {
        workingDir = File("build${File.separator}test-$name$suffix").absoluteFile
        workingDir.deleteRecursively()
        workingDir.mkdirs()
        val testResources = File("src/test/resources")
        val originalProjectDir = testResources.resolve("projects/$name").apply { checkExists() }
        val projectDir = workingDir.resolve(name).apply { mkdirs() }
        originalProjectDir.listFiles().forEach { it.copyRecursively(projectDir.resolve(it.name)) }

        // Add an empty setting.gradle
        projectDir.resolve("settings.gradle").writeText("// this file is intentionally left empty")

        Project(projectDir = projectDir).fn()
    }
}