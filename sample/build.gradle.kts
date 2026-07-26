plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    // Injects `<ClassName>Validator.validate(this)` into every @Validated class's constructor.
    // Requires :compiler-plugin and :compiler-plugin-gradle to have been published to
    // mavenLocal at least once first — see .claude/references/compiler-plugins.md.
    id("io.github.abdullahkhan118.fieldvalidator") version "0.1.0"
    application
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
    ksp(project(":processor"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("io.github.abdullahkhan118.fieldvalidator.sample.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
