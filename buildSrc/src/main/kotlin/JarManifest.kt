import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.withType

internal fun Project.configureImplementationJarManifest(taskName: String) {
    tasks.withType<Jar>().configureEach {
        if (name != taskName) return@configureEach
        manifest.attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "JetBrains"
            )
        )
    }
}
