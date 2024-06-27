import groovy.util.Node
import groovy.util.NodeList

plugins {
    id("publish-with-javadoc-conventions")
}

afterEvaluate {
    publishing.publications {
        val jvm = getByName<MavenPublication>("jvm")
        getByName<MavenPublication>("kotlinMultiplatform") {
            val platformPublication = jvm
            var platformXml: XmlProvider? = null
            platformPublication.pom.withXml { platformXml = this }
            pom.withXml {
                val root = asNode()
                // Remove the original content and add the content from the platform POM:
                root.children().toList().forEach { node -> root.remove(node as Node?) }
                platformXml?.asNode()?.children()?.forEach { node -> root.append(node as Node) }

                // Adjust the self artifact id, as it should match the root module's coordinates:
                ((root.get("artifactId") as NodeList).get(0) as Node).setValue(artifactId)

                // Set packaging to POM to indicate that there's no artifact:
                root.appendNode("packaging", "pom")

                // Remove the original platform dependencies and add a single dependency on the platform module:
                val allDependencies = root.get("dependencies") as NodeList
                if (allDependencies.isNotEmpty()) {
                    val dependencies = allDependencies.get(0) as Node
                    dependencies.children().toList().forEach { node -> dependencies.remove(node as Node) }
                    val singleDependency = dependencies.appendNode("dependency")
                    singleDependency.appendNode("groupId", platformPublication.groupId)
                    singleDependency.appendNode("artifactId", platformPublication.artifactId)
                    singleDependency.appendNode("version", platformPublication.version)
                    singleDependency.appendNode("scope", "compile")
                }
            }
            tasks.matching { it.name == "generatePomFileForKotlinMultiplatformPublication"}.configureEach {
                dependsOn(tasks["generatePomFileFor${platformPublication.name.capitalize()}Publication"])
            }
        }
    }
}