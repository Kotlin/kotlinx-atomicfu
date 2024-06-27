plugins {
    `maven-publish`
    signing
}

publishing {
    repositories {
        maven {
            // TODO(Dmitrii Krasnov): add repository name, but before check
            //  that TC build configuration does not set task names explicitly
            url = mavenRepositoryUri()
            credentials {
                username = project.getSensitiveProperty("libs.sonatype.user")
                password = project.getSensitiveProperty("libs.sonatype.password")
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name = project.name
            description = "AtomicFU utilities"
            url = "https://github.com/Kotlin/kotlinx.atomicfu"

            licenses {
                license {
                    name = "The Apache Software License, Version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    distribution = "repo"
                }
            }

            developers {
                developer {
                    id = "JetBrains"
                    name = "JetBrains Team"
                    organization = "JetBrains"
                    organizationUrl = "https://www.jetbrains.com"
                }
            }

            scm {
                url = "https://github.com/Kotlin/kotlinx.atomicfu"
            }
        }


        signPublicationIfKeyPresent(project, this)
    }

    tasks.withType<AbstractPublishToMaven>().configureEach {
        mustRunAfter(tasks.withType<Sign>())
    }

    // NOTE: This is a temporary WA, see KT-61313.
    tasks.withType<Sign>().configureEach sign@{
        val pubName = name.substringAfter("sign").substringBefore("Publication")

        // Task ':linkDebugTest<platform>' uses this output of task ':sign<platform>Publication' without declaring an explicit or implicit dependency
        tasks.findByName("linkDebugTest$pubName")?.let { linkTask ->
            linkTask.mustRunAfter(this@sign)
        }
        // Task ':compileTestKotlin<platform>' uses this output of task ':sign<platform>Publication' without declaring an explicit or implicit dependency
        tasks.findByName("compileTestKotlin$pubName")?.let { compileTask ->
            compileTask.mustRunAfter(this@sign)
        }
    }
}
