import org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenLocal()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("org.jetbrains.kotlin:kotlin-script-runtime")
    implementation(kotlin("script-runtime"))
}

val kotlin_version = providers.gradleProperty("kotlin_version").orNull
val atomicfu_snapshot_version = providers.gradleProperty("version").orNull

sourceSets {
    create("mavenTest") {
        compileClasspath += files(sourceSets.main.get().output, configurations.testRuntimeClasspath)
        runtimeClasspath += output + compileClasspath
        
        dependencies {
            implementation("org.jetbrains.kotlinx:atomicfu-jvm:$atomicfu_snapshot_version")
        }
    }

    create("functionalTest") {
        compileClasspath += files(sourceSets.main.get().output, configurations.testRuntimeClasspath)
        runtimeClasspath += output + compileClasspath

        dependencies {
            testImplementation(gradleTestKit())
            api("org.ow2.asm:asm:9.3")
            api("org.ow2.asm:asm-commons:9.3")
        }
    }
}

val mavenTest by tasks.registering(Test::class) {
    testClassesDirs = sourceSets["mavenTest"].output.classesDirs
    classpath = sourceSets["mavenTest"].runtimeClasspath
    
    dependsOn(":atomicfu:publishToMavenLocal")
}

val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath

    systemProperties["kotlinVersion"] = kotlin_version
    systemProperties["atomicfuVersion"] = atomicfu_snapshot_version
    
    dependsOn("publishToMavenLocal")
}

tasks.check { dependsOn(mavenTest, functionalTest) }

// Setup K/N infrastructure to use klib utility in tests
// TODO: klib checks are skipped for now because of this problem KT-61143
val Project.konanHome: String
get() = rootProject.properties["kotlin.native.home"]?.toString()
        ?: NativeCompilerDownloader(project).compilerDirectory.absolutePath

val embeddableJar = File(project.konanHome).resolve("konan/lib/kotlin-native-compiler-embeddable.jar")

tasks.withType<Test> {
    // Pass the path to native jars
    systemProperty("kotlin.native.jar", embeddableJar)
}
