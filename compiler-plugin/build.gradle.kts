plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

dependencies {
    // Provided by kotlinc itself at compile time — must NOT be bundled into our jar, or we'd
    // ship (and potentially conflict with) the compiler's own classes at runtime.
    compileOnly(libs.kotlin.compiler.embeddable)
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
