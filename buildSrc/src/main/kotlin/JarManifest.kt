import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

internal fun Project.configureImplementationJarManifest(taskName: String) {
    tasks.matching { it.name == taskName }.configureEach {
        this as Jar
        manifest.attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "JetBrains"
            )
        )
    }
}
