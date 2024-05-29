plugins {
//     Regular java modules need 'java-library' plugin for proper publication
    `java-library`
    kotlin("jvm")
    id("publish-conventions")
    id("common-conventions")
    id("kotlin-base-conventions")
}

// MPP projects pack their sources automatically, java libraries need to explicitly pack them
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from("src/main/kotlin")
}

publishing {
    // Configure java publications for non-MPP projects
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}

kotlin {
    jvmToolchain(8)
}