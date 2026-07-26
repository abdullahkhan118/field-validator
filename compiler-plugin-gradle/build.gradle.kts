plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    // Only the types needed to describe "add this compiler plugin to kotlinc's classpath" —
    // this module does NOT depend on :compiler-plugin itself. It only needs that module's
    // *Maven coordinates* as a string (see FieldValidatorGradlePlugin.getPluginArtifact),
    // which the Kotlin Gradle plugin resolves separately at consumer build time.
    compileOnly(libs.kotlin.gradle.plugin.api)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // `java-gradle-plugin` implicitly adds `gradleApi()`, which bundles the Gradle
        // distribution's own Kotlin stdlib/reflect — on Gradle 9.4 that's a newer Kotlin
        // (metadata version 2.3.0) than this module's own Kotlin compiler (2.0.21, which
        // only understands metadata up to 2.1.0). Reading gradleApi()'s bundled stdlib
        // metadata would otherwise fail this module's compilation outright; this flag is
        // the compiler's own suggested escape hatch, safe here since we never call into the
        // handful of stdlib functions whose *behavior* actually changed across that gap.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

// `java-gradle-plugin` + `maven-publish` together auto-create both the plugin's implementation
// publication and its "marker" publication (the artifact `plugins { id("...") }` actually
// resolves against). No manual `publishing { publications { ... } }` block needed here — see
// :compiler-plugin's build.gradle.kts, which isn't a Gradle plugin and so does need one.
gradlePlugin {
    plugins {
        create("fieldValidatorPlugin") {
            id = "io.github.abdullahkhan118.fieldvalidator"
            implementationClass =
                "io.github.abdullahkhan118.fieldvalidator.compilerplugin.gradle.FieldValidatorGradlePlugin"
        }
    }
}
