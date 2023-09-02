@file:JvmName("MavenPomConfiguration")

import org.gradle.api.*
import org.gradle.api.publish.maven.*

fun MavenPom.configureMavenPluginPomAttributes(
    project: Project,
    outputDir: String
) {
    val customKotlinRepoURL = getCustomKotlinRepositoryURL(project)
    val buildSnapshots = project.hasProperty("build_snapshot_train")
    name.set(project.name)
    packaging = "maven-plugin"
    description.set("Atomicfu Maven Plugin")

    withXml {
        with(asNode()) {
            with(appendNode("build")) {
                appendNode("directory", project.buildDir)
                appendNode("outputDirectory", outputDir)
            }
            appendNode("properties")
                .appendNode("project.build.sourceEncoding", "UTF-8")
            with(appendNode("repositories")) {
                if (!customKotlinRepoURL.isNullOrEmpty()) {
                    with(appendNode("repository")) {
                        appendNode("id", "dev")
                        appendNode("url", customKotlinRepoURL)
                    }
                }
                if (buildSnapshots) {
                    with(appendNode("repository")) {
                        appendNode("id", "kotlin-snapshots")
                        appendNode("url", "https://oss.sonatype.org/content/repositories/snapshots")
                    }
                }
            }
        }
    }
}
