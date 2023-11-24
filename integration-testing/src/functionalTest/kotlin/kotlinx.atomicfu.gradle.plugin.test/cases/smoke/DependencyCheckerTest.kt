/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.cases.smoke

import kotlinx.atomicfu.gradle.plugin.test.framework.runner.BuildResult
import java.io.File
import kotlin.test.*

class DependencyParserSmokeTest {
    private val tempFile = File.createTempFile("sample", null)
    
    private val dependencies = "> Task :dependencies\n" +
            "\n" +
            "------------------------------------------------------------\n" +
            "Root project 'jvm-sample'\n" +
            "------------------------------------------------------------\n" +
            "compileClasspath - Compile classpath for null/main.\n" +
            "+--- org.jetbrains.kotlinx:atomicfu-jvm:0.23.1-SNAPSHOT\n" +
            "+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.0\n" +
            "|    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0\n" +
            "|    \\--- org.jetbrains:annotations:13.0\n" +
            "\\--- org.jetbrains.kotlin:kotlin-test-junit:1.9.0\n" +
            "     +--- org.jetbrains.kotlin:kotlin-test:1.9.0\n" +
            "     |    \\--- org.jetbrains.kotlin:kotlin-stdlib:1.9.0 (*)\n" +
            "     \\--- junit:junit:4.13.2\n" +
            "          \\--- org.hamcrest:hamcrest-core:1.3\n" +
            "\n" +
            "compileOnly - Compile only dependencies for null/main. (n)\n" +
            "\\--- org.jetbrains.kotlinx:atomicfu-jvm:0.23.1-SNAPSHOT (n)\n" +
            "\n" +
            "compileOnlyDependenciesMetadata\n" +
            "\\--- org.jetbrains.kotlinx:atomicfu-jvm:0.23.1-SNAPSHOT\n" +
            "\n" +
            "default - Configuration for default artifacts. (n)\n" +
            "No dependencies\n" +
            "\n" +
            "implementation - Implementation only dependencies for null/main. (n)\n" +
            "+--- org.jetbrains.kotlin:kotlin-stdlib (n)\n" +
            "\\--- org.jetbrains.kotlin:kotlin-test-junit (n)\n" +
            "\n" +
            "implementationDependenciesMetadata\n" +
            "+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.0\n" +
            "|    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0\n" +
            "|    \\--- org.jetbrains:annotations:13.0\n" +
            "\\--- org.jetbrains.kotlin:kotlin-test-junit:1.9.0\n" +
            "     +--- org.jetbrains.kotlin:kotlin-test:1.9.0\n" +
            "     |    +--- org.jetbrains.kotlin:kotlin-test-common:1.9.0\n" +
            "     |    |    \\--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0\n" +
            "     |    \\--- org.jetbrains.kotlin:kotlin-test-annotations-common:1.9.0\n" +
            "     |         \\--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0\n" +
            "     \\--- junit:junit:4.13.2\n" +
            "          \\--- org.hamcrest:hamcrest-core:1.3\n" +
            "\n"
    
    @Test
    fun testGetDependenciesForConfig() {
        tempFile.bufferedWriter().use { out ->
            out.write(dependencies)
        }
        val buildResult = BuildResult(0, tempFile)
        assertEquals(
            listOf(
                "org.jetbrains.kotlin:kotlin-stdlib", 
                "org.jetbrains.kotlin:kotlin-test-junit"
            ),
            buildResult.getDependenciesForConfig("implementation")
        )
        assertEquals(
            emptyList(),
            buildResult.getDependenciesForConfig("default")
        )
        assertEquals(
            listOf(
                "org.jetbrains.kotlinx:atomicfu-jvm:0.23.1-SNAPSHOT",
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.0",
                "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0",
                "org.jetbrains:annotations:13.0",
                "org.jetbrains.kotlin:kotlin-test-junit:1.9.0",
                "org.jetbrains.kotlin:kotlin-test:1.9.0",
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.0",
                "junit:junit:4.13.2",
                "org.hamcrest:hamcrest-core:1.3"
            ),
            buildResult.getDependenciesForConfig("compileClasspath")
        )
    }
}
