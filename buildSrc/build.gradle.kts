plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.build.tools.api) // runtime dependency of KGP
}

kotlin {
    // KMP KGP has an opt-in type in the accessors. Enable -Werror after bumping Gradle to 9+ https://github.com/gradle/gradle/issues/32019
    // compilerOptions.allWarningsAsErrors = true
}
