plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.build.tools.api) // runtime dependency of KGP
}
