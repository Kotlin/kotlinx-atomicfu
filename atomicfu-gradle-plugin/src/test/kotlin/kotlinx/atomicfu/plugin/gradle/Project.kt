/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

class Project(val projectDir: File) {
    init {
        projectDir.resolve("build.gradle").modify {
            buildScript + "\n\n" + it
        }
    }

    private var isDebug = false
    private var printStdout = false

    @Deprecated("Should be used for debug only!")
    @Suppress("unused")
    fun debug() {
        isDebug = true
    }

    /**
     * Redirects Gradle runner output to stdout. Useful for debugging.
     */
    @Deprecated("Should be used for debug only!")
    @Suppress("unused")
    fun printStdout() {
        printStdout = true
    }

    fun gradle(vararg tasks: String): GradleRunner =
            GradleRunner.create()
                .withDebug(isDebug)
                .withProjectDir(projectDir)
                .withArguments(*(defaultArguments() + tasks))
                .run {
                    if (printStdout) {
                        forwardStdOutput(System.out.bufferedWriter())
                    } else {
                        this
                    }
                }

    fun build(vararg tasks: String, fn: BuildResult.() -> Unit = {}) {
        val gradle = gradle(*tasks)
        val buildResult = gradle.build()
        buildResult.fn()
    }

    @Suppress("unused")
    fun buildAndFail(vararg tasks: String, fn: BuildResult.() -> Unit = {}) {
        val gradle = gradle(*tasks)
        val buildResult = gradle.buildAndFail()
        buildResult.fn()
    }

    private fun defaultArguments(): Array<String> =
        arrayOf("--stacktrace")

    companion object {
        private fun readFileList(fileName: String): String {
            val resource = Project::class.java.classLoader.getResource(fileName)
                    ?: throw IllegalStateException("Could not find resource '$fileName'")
            val files = File(resource.toURI())
                    .readLines()
                    .map { File(it).absolutePath.replace("\\", "\\\\") } // escape backslashes in Windows paths
            return files.joinToString(", ") { "'$it'" }
        }

        private val buildScript = run {
            """
                buildscript {
                    dependencies {
                        classpath files(${readFileList("plugin-classpath.txt")})
                    }
                }

                repositories {
                    jcenter()
                    mavenCentral()
                    maven { url 'https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev' }
                    maven { url 'https://dl.bintray.com/kotlin/kotlin-eap' }
                    maven { url 'https://dl.bintray.com/kotlin/kotlin-dev' }
                    maven { url 'https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev' }
                    maven { url "https://oss.sonatype.org/content/repositories/snapshots" }
                }

                def atomicfuJvm = files(${readFileList("atomicfu-jvm.txt")})
                def atomicfuJs = files(${readFileList("atomicfu-js.txt")})
                def atomicfuMetadata = files(${readFileList("atomicfu-metadata.txt")})
            """.trimIndent()
        }
    }
}
