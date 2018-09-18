package kotlinx.atomicfu.plugin.gradle

import org.gradle.internal.impldep.com.google.common.io.Files
import org.junit.After
import org.junit.Before
import java.io.File

abstract class BaseKotlinGradleTest {
    private lateinit var workingDir: File

    @Before
    fun setUp() {
        workingDir = Files.createTempDir()
    }

    @After
    fun tearDown() {
        workingDir.deleteRecursively()
    }

    fun project(name: String, fn: Project.() -> Unit) {
        val testResources = File("src/test/resources")
        val originalProjectDir = testResources.resolve("projects/$name").apply { checkExists() }
        val projectDir = workingDir.resolve(name).apply { mkdirs() }
        originalProjectDir.listFiles().forEach { it.copyRecursively(projectDir.resolve(it.name)) }

        Project(projectDir = projectDir).fn()
    }
}