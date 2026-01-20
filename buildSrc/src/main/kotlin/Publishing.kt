/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

import org.gradle.api.*
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.*
import org.gradle.kotlin.dsl.maven
import org.gradle.plugins.signing.*
import java.net.*

// Pom configuration

fun signPublicationIfKeyPresent(project: Project, publication: MavenPublication) {
    val keyId = project.getSensitiveProperty("libs.sign.key.id")
    val signingKey = project.getSensitiveProperty("libs.sign.key.private")
    val signingKeyPassphrase = project.getSensitiveProperty("libs.sign.passphrase")
    if (!signingKey.isNullOrBlank()) {
        project.extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)
            sign(publication)
        }
    }
}

fun PublishingExtension.addPublishingRepositoryIfPresent(project: Project) {
    val repoUrl = System.getenv("libs.repo.url")
    if (!repoUrl.isNullOrBlank()) {
        repositories {
            maven {
                // if you change the name, you should change the name of the credential properties on CI as well
                name = "MavenRepositoryForPublishing"
                url = URI(repoUrl)

                // we use such type of credential because of the configuration cache problems with other types:
                // https://github.com/gradle/gradle/issues/24040
                credentials(PasswordCredentials::class.java)
            }
        }
    }
    repositories {
        maven(project.rootProject.layout.buildDirectory.dir("repo")) {
            name = "buildRepo"
        }
    }
}

internal fun Project.getSensitiveProperty(name: String): String? {
    return project.findProperty(name) as? String ?: System.getenv(name)
}
