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

infix fun <T> Property<T>.by(value: T) {
    set(value)
}

fun MavenPom.configureMavenCentralMetadata(project: Project) {
    name by project.name
    description by "AtomicFU utilities"
    url by "https://github.com/Kotlin/kotlinx.atomicfu"

    licenses {
        license {
            name by "The Apache Software License, Version 2.0"
            url by "https://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution by "repo"
        }
    }

    developers {
        developer {
            id by "JetBrains"
            name by "JetBrains Team"
            organization by "JetBrains"
            organizationUrl by "https://www.jetbrains.com"
        }
    }

    scm {
        url by "https://github.com/Kotlin/kotlinx.atomicfu"
    }
}

fun configureBintrayPublication(rh: RepositoryHandler, project: Project) {
    rh.maven {
        val user = "kotlin"
        val repo = "kotlinx"
        val name = "kotlinx.atomicfu"
        url = URI("https://api.bintray.com/maven/$user/$repo/$name/;publish=0;override=0")

        credentials {
            username = project.findProperty("bintrayUser") as? String ?: System.getenv("BINTRAY_USER")
            password = project.findProperty("bintrayApiKey") as? String ?: System.getenv("BINTRAY_API_KEY")
        }
    }
}

fun mavenRepositoryUri(): URI {
    // TODO -SNAPSHOT detection can be made here as well
    val repositoryId: String? = System.getenv("libs.repository.id")
    return if (repositoryId == null) {
        // Using implicitly created staging, for MPP it's likely to be the mistake
        System.err.println("Warning: using an implicitly created staging for atomicfu")
        URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
    } else {
        URI("https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId")
    }
}

fun configureMavenPublication(rh: RepositoryHandler) {
    rh.maven {
        url = mavenRepositoryUri()
        credentials {
            username = System.getenv("libs.sonatype.user")
            password = System.getenv("libs.sonatype.password")
        }
    }
}

fun signPublicationIfKeyPresent(project: Project, publication: MavenPublication) {
    val keyId = System.getenv("libs.sign.key.id")
    val signingKey = System.getenv("libs.sign.key.private")
    val signingKeyPassphrase = System.getenv("libs.sign.passphrase")
    if (!signingKey.isNullOrBlank()) {
        project.extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)
            sign(publication)
        }
    }
}

