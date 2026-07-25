plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}

kotlin {
    jvmToolchain(17)
}
