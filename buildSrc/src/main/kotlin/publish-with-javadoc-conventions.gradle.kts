plugins {
    id("base-publish-conventions")
}

val javadocJar by tasks.register<Jar>("javadocJar") {
    description = "Create a Javadoc JAR. Empty by default."
    // Create an empty Javadoc JAR to satisfy Maven Central requirements.
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name != "kotlinMultiplatform") { // The root module gets the JVM's javadoc JAR
            artifact(javadocJar)
        }
    }
}