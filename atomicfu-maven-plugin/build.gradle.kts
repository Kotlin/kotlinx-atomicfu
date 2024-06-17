/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `maven-publish`
    id("kotlin-jvm-conventions")
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    api(project(":atomicfu-transformer"))
    api(libs.bundles.maven)
}

val outputDir = tasks.compileKotlin.get().destinationDirectory

publishing.publications {
    named<MavenPublication>("maven").configure {
        pom.configureMavenPluginPomAttributes(project, outputDir.get().asFile.path)
        pom.packaging = "maven-plugin"
    }
}

val mavenUserHome: String? = System.getProperty("maven.user.home")
val mavenRepoLocal: String? = System.getProperty("maven.repo.local")

val generatePomFileForMavenPublication by tasks.getting(GenerateMavenPom::class)

// runs the plugin description generator
val generatePluginDescriptor by tasks.registering(Exec::class) {

    dependsOn(generatePomFileForMavenPublication)

    dependsOn(project(":atomicfu-transformer").tasks.named("publishToMavenLocal"))

    val pluginDescriptorFile = outputDir.file("META-INF/maven/plugin.xml")

    workingDir = projectDir
    val isWindows = System.getProperty("os.name").lowercase().indexOf("windows") >= 0
    val args = if (isWindows) mutableListOf("cmd", "/c", "mvnw.cmd") else mutableListOf("sh", "./mvnw")
    if (mavenUserHome != null) args.add("-Dmaven.user.home=${File(mavenUserHome).absolutePath}")
    if (mavenRepoLocal != null) args.add("-Dmaven.repo.local=${File(mavenRepoLocal).absolutePath}")
    args.addAll(
        listOf(
            "--settings", "./settings.xml",
            "--errors",
            "--batch-mode",
            "--file", generatePomFileForMavenPublication.destination.toString(),
            "org.apache.maven.plugins:maven-plugin-plugin:3.5.1:descriptor"
        )
    )
    commandLine = args
    doLast {
        val descriptorFile = pluginDescriptorFile.get().asFile
        require(descriptorFile.exists()) { "$descriptorFile: was not generated" }
        logger.info("Plugin descriptor is generated in $descriptorFile")
    }
}

tasks.jar {
    dependsOn(generatePluginDescriptor)
}
