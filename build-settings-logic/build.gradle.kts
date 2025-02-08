plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.develocity)
}

kotlin {
    compilerOptions.allWarningsAsErrors = true
}