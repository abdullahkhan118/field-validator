pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // The `field-validator-plugin` id (applied in sample/build.gradle.kts) is resolved from
        // here: `:compiler-plugin-gradle` publishes its plugin marker to mavenLocal as part of
        // the local dev workflow. See .claude/references/compiler-plugins.md for the full story.
        mavenLocal()
    }
}

rootProject.name = "field-validator"

include(
    ":annotations",
    ":runtime",
    ":processor",
    ":compiler-plugin",
    ":compiler-plugin-gradle",
    ":sample",
)
