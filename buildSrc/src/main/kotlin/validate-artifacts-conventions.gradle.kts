/*
 * Copyright 2010-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.base
import java.nio.file.FileVisitResult
import kotlin.io.path.name
import kotlin.io.path.*

plugins {
    base
}

abstract class ArtifactsCheckTask: DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifactsDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifactsFile: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val validateVersion: Property<String>

    @get:Optional
    @get:Input
    abstract val dumpArtifacts: Property<Boolean>

    @TaskAction
    fun check() {
        val root = artifactsDirectory.get().asFile.toPath()
        val justDump = dumpArtifacts.getOrElse(false)
        val expectedVersion = if (justDump) null else validateVersion.orNull

        val actualArtifacts = buildSet {
            gavScanner(root) { gav ->
                if (expectedVersion == null) {
                    add("${gav.groupId}:${gav.artifactId}")
                } else {
                    add("${gav.groupId}:${gav.artifactId}:${gav.version}")
                }
            }
        }.toSortedSet()

        if (justDump) {
            artifactsFile.asFile.get().bufferedWriter().use { writer ->
                actualArtifacts.forEach {
                    writer.appendLine(it)
                }
            }
            return
        }

        val expectedArtifacts = artifactsFile.asFile.get().readLines().map {
          if (expectedVersion == null) it else "$it:${expectedVersion}"
        }.toSet()

        if (expectedArtifacts == actualArtifacts) {
            logger.lifecycle("All artifacts are published")
        } else {
            val missedArtifacts = expectedArtifacts - actualArtifacts
            val unknownArtifacts = actualArtifacts - expectedArtifacts
            val message = "The published artifacts differ from the expected ones." +
                    (if (missedArtifacts.isNotEmpty()) missedArtifacts.joinToString(prefix = "\n\tMissing artifacts: ") else "") +
                    (if (unknownArtifacts.isNotEmpty()) unknownArtifacts.joinToString(prefix = "\n\tUnknown artifacts: ") else "")

            logger.error(message)
            throw GradleException("The published artifacts differ from the expected ones")
        }
    }

    private data class ArtifactInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val filename: String
    )

    @OptIn(ExperimentalPathApi::class)
    private inline fun gavScanner(repository: java.nio.file.Path, crossinline onArtifact: (ArtifactInfo) -> Unit) {
        repository.visitFileTree(fileVisitor {
            // Skip hidden directories
            onPreVisitDirectory { directory, _ ->
                if (directory.name.startsWith(".")) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
            }
            // Proper artifacts have the following format path:
            // group/id/prefix/artifactId/version/artifactId-version[-.]*
            onVisitFile { file, _ ->
                val relativePath = repository.relativize(file) ?: return@onVisitFile FileVisitResult.CONTINUE
                val fileName = relativePath.name
                val versionPath = relativePath.parent ?: return@onVisitFile FileVisitResult.CONTINUE
                val artifactIdPath = versionPath.parent ?: return@onVisitFile FileVisitResult.CONTINUE
                val groupIdPath = artifactIdPath.parent ?: return@onVisitFile FileVisitResult.CONTINUE

                val version = versionPath.name
                val artifactId = artifactIdPath.name
                val groupId = groupIdPath.joinToString(".") { it.name }

                val av = if (version.endsWith("-SNAPSHOT")) {
                    "$artifactId-${version.dropLast("-SNAPSHOT".length)}"
                } else {
                    "$artifactId-$version"
                }

                if (!fileName.startsWith(av)) {
                    return@onVisitFile FileVisitResult.CONTINUE
                }

                if ((fileName.length < av.length + 2) || (fileName[av.length] != '.' && fileName[av.length] != '-')) {
                    return@onVisitFile FileVisitResult.CONTINUE
                }

                onArtifact(ArtifactInfo(groupId, artifactId, version, fileName))

                return@onVisitFile FileVisitResult.CONTINUE
            }
        })
    }
}

if (project.properties.contains("kotlinx.atomicfu.validateDeployment")) {
    tasks.register("validateDeployment", ArtifactsCheckTask::class.java) {
        artifactsFile.set(project.layout.projectDirectory.file("gradle/artifacts.txt"))
        artifactsDirectory.set(
            file(project.properties["kotlinx.atomicfu.validateDeployment"] as String)
        )
        if (project.properties.getOrDefault("kotlinx.atomicfu.validateDeployment.dump", "false").toString().toBoolean()) {
            dumpArtifacts.set(true)
        }
        validateVersion.set(project.properties.getOrDefault("kotlinx.atomicfu.validateDeployment.version", null)?.toString())
    }
}
