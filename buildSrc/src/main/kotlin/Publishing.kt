/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

import org.gradle.api.*
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.provider.*
import org.gradle.api.publish.maven.*
import org.gradle.plugins.signing.*
import java.net.*

// Pom configuration

fun mavenRepositoryUri(): URI {
    if (getSensitiveProperty("libs.publication_repository") == "central") {
        val repoUrl = getSensitiveProperty("libs.repo.url")
            ?: throw IllegalArgumentException("Using central repository for deployment implies presence of libs.repo.url property")
        return URI(repoUrl)
    }
    val repositoryId: String? = System.getenv("libs.repository.id")
    return if (repositoryId == null) {
        // Using implicitly created staging, for MPP it's likely to be a mistake because
        // publication on TeamCity will create 3 independent staging repositories
        URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
    } else {
        URI("https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId")
    }
}

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

internal fun Project.getSensitiveProperty(name: String): String? {
    return project.findProperty(name) as? String ?: System.getenv(name)
}
