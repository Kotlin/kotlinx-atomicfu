@file:JvmName("MavenPomConfiguration")

import org.gradle.api.*
import org.gradle.api.publish.maven.*

fun MavenPom.configureMavenPluginPomAttributes(
    project: Project,
    outputDir: String
) {
    val kotlinDevRepoUrl = getKotlinDevRepositoryUrl(project)
    val buildSnapshots = project.rootProject.properties["build_snapshot_train"] != null
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
                if (!kotlinDevRepoUrl.isNullOrEmpty()) {
                    with(appendNode("repository")) {
                        appendNode("id", "dev")
                        appendNode("url", kotlinDevRepoUrl)
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
