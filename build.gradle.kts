plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

allprojects {
    group = "io.github.abdullahkhan118.fieldvalidator"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
