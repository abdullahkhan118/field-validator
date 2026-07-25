plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(17)
}
