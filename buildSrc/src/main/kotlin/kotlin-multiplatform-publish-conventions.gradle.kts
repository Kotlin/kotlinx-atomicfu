import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

plugins {
    id("publish-with-javadoc-conventions")
}
afterEvaluate {

    val generatePomFileForJvmPublication by tasks.getting(GenerateMavenPom::class)

    tasks.named<GenerateMavenPom>("generatePomFileForKotlinMultiplatformPublication").configure {

        dependsOn(generatePomFileForJvmPublication)

        val jvmPomFile = generatePomFileForJvmPublication.destination
        doLast {
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(jvmPomFile).apply {
                // strip whitespace, otherwise pretty-printing output has blank lines
                removeWhitespaceNodes()
                // set standalone=true to prevent `standalone="no"` in the output
                xmlStandalone = true
            }

            val jvmDoc = builder.documentElement
            val jvmArtifactId = jvmDoc.getElement("artifactId").textContent
            // Adjust the self artifact id, as it should match the root module's coordinates:
            val kmpPomDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(destination).documentElement
            jvmDoc.getElement("artifactId").textContent =
                kmpPomDoc.getElement("artifactId").textContent

            // Remove the original platform dependencies and add a single dependency on the platform module:
            val dependencies = jvmDoc.getElement("dependencies")
            jvmDoc.removeChild(dependencies)
            jvmDoc.appendChild(builder.createElement("dependencies") {
                appendChild(
                    builder.createElement("dependency") {
                        appendChild(builder.createElement("groupId", jvmDoc.getElement("groupId").textContent))
                        appendChild(builder.createElement("artifactId", jvmArtifactId))
                        appendChild(builder.createElement("version", jvmDoc.getElement("version").textContent))
                        appendChild(builder.createElement("scope", "compile"))
                    }
                )
            })

            // Set packaging to POM to indicate that there's no artifact:
            jvmDoc.appendChild(builder.createElement("packaging", "pom"))


            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }.transform(DOMSource(jvmDoc), StreamResult(destination))
        }
    }
}