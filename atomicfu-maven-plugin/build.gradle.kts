import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.kotlin.dsl.support.*

/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `maven-publish`
    id("kotlin-jvm-publish-conventions")
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    api(project(":atomicfu-transformer"))
    api(libs.bundles.maven)
}

publishing.publications {
    named<MavenPublication>("maven").configure {
        pom.configureMavenPluginPomAttributes(project.name)
    }
}

tasks.generatePomFileForMavenPublication {
    val customKotlinRepoURL = getCustomKotlinRepositoryURL(project)
    inputs.property("customKotlinRepoURL", customKotlinRepoURL).optional(true)

    val buildSnapshots = project.hasProperty("build_snapshot_train")
    inputs.property("buildSnapshots", buildSnapshots).optional(true)

    val buildDirectory = project.layout.buildDirectory

    val outputDir = tasks.compileKotlin.map { it.destinationDirectory.get() }
    inputs.dir(outputDir)
        .withPropertyName("compileKotlinOutputDir")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    doLast("Configure maven plugin pom attributes") {
        val originalXml = destination
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(originalXml).apply {
            // strip whitespace, otherwise pretty-printing output has blank lines
            removeWhitespaceNodes()
            // set standalone=true to prevent `standalone="no"` in the output
            xmlStandalone = true
        }
        val projectNode = builder.documentElement
        projectNode.appendChild(
            builder.createElement("build") {
                appendChild(builder.createElement("directory", buildDirectory.get().asFile.invariantSeparatorsPath))
                appendChild(builder.createElement("outputDirectory", outputDir.get().asFile.invariantSeparatorsPath))
            }
        )
        projectNode.appendChild(
            builder.createElement("properties") {
                appendChild(builder.createElement("project.build.sourceEncoding", "UTF-8"))
            }
        )
        projectNode.appendChild(
            builder.createElement("repositories") {
                if (!customKotlinRepoURL.isNullOrEmpty()) {
                    appendChild(builder.createElement("repository") {
                        appendChild(builder.createElement("id", "dev"))
                        appendChild(builder.createElement("url", customKotlinRepoURL))
                    })
                }
                if (buildSnapshots) {
                    appendChild(builder.createElement("repository") {
                        appendChild(builder.createElement("id", "kotlin-snapshots"))
                        appendChild(
                            builder.createElement(
                                "url",
                                "https://oss.sonatype.org/content/repositories/snapshots"
                            )
                        )
                    })
                }
            }
        )

        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }.transform(DOMSource(builder), StreamResult(destination))
    }
}

// runs the plugin description generator
val generatePluginDescriptor by tasks.registering {

    val exec = serviceOf<ExecOperations>()

    val pomFile = tasks.generatePomFileForMavenPublication.map { it.destination }
    // Don't depend on the file contents, because it contains absolute paths and so is different per machine
    dependsOn(tasks.generatePomFileForMavenPublication)

    outputs.cacheIf { true }

    // TODO(Dmitrii Krasnov): fix this for project isolation compatibility
    dependsOn(project(":atomicfu-transformer").tasks.named("publishToMavenLocal"))

    val outputDir = tasks.compileKotlin.map { it.destinationDirectory.get() }
    inputs.dir(outputDir)
        .withPropertyName("compileKotlinOutputDir")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    
    val pluginDescriptorFile = outputDir.map { it.asFile.resolve("META-INF/maven/plugin.xml") }
    outputs.file(pluginDescriptorFile)
        .withPropertyName("pluginDescriptorFile")

    // redeclare the projectDir to support Configuration Cache
    val projectDir = projectDir

    doLast {

        exec.exec {
            workingDir = projectDir

            val mavenUserHome: String? = System.getProperty("maven.user.home")
            val mavenRepoLocal: String? = System.getProperty("maven.repo.local")

            val isWindows = "windows" in System.getProperty("os.name").lowercase()
            val args = if (isWindows) mutableListOf("cmd", "/c", "mvnw.cmd") else mutableListOf("sh", "./mvnw")
            if (mavenUserHome != null) args.add("-Dmaven.user.home=${File(mavenUserHome).invariantSeparatorsPath}")
            if (mavenRepoLocal != null) args.add("-Dmaven.repo.local=${File(mavenRepoLocal).invariantSeparatorsPath}")
            args.addAll(
                listOf(
                    "--settings", "./settings.xml",
                    "--errors",
                    "--batch-mode",
                    "--file", pomFile.get().invariantSeparatorsPath,
                    "org.apache.maven.plugins:maven-plugin-plugin:3.5.1:descriptor"
                )
            )
            commandLine = args
        }

        val descriptorFile = pluginDescriptorFile.get()
        require(descriptorFile.exists()) { "$descriptorFile: was not generated" }
        logger.info("Plugin descriptor is generated in $descriptorFile")
    }
}

tasks.jar {
    dependsOn(generatePluginDescriptor)
}
tasks.apiBuild {
    dependsOn(generatePluginDescriptor)
}
