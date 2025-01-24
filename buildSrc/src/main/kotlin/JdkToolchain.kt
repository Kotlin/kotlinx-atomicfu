import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

fun JavaExec.launcherForJdk(jdkVersion: Int) {
    val toolchainService = project.extensions.getByType(JavaToolchainService::class.java)
    javaLauncher.set(
        toolchainService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(jdkVersion))
        }
    )
}

fun Test.launcherForJdk(jdkVersion: Int) {
    val toolchainService = project.extensions.getByType(JavaToolchainService::class.java)
    javaLauncher.set(
        toolchainService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(jdkVersion))
        }
    )
}
