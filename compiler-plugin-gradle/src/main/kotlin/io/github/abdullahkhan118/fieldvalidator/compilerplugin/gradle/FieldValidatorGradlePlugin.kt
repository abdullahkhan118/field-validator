package io.github.abdullahkhan118.fieldvalidator.compilerplugin.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * The Gradle-facing half of this project's compiler plugin. Applying `id("io.github.
 * abdullahkhan118.fieldvalidator")` (see `sample/build.gradle.kts`) to a module is what wires
 * `:compiler-plugin`'s jar onto `kotlinc`'s `-Xplugin=` classpath for that module's
 * compilations — the same mechanism `kotlin("plugin.serialization")` uses to wire up
 * kotlinx.serialization's own compiler plugin.
 *
 * This class contains no actual validation logic — it only *describes* the plugin (its id, and
 * where to fetch its jar from) to the Kotlin Gradle plugin, which does the classpath wiring.
 * The real behavior lives in `:compiler-plugin`'s `ValidatedIrGenerationExtension`.
 *
 * ## Why this is a separate module from :compiler-plugin
 *
 * `:compiler-plugin` depends on `kotlin-compiler-embeddable` (the actual compiler internals —
 * IR, FIR, etc.) and is loaded *inside* `kotlinc` itself, once per compilation, by every module
 * that applies this plugin. This module depends on the *Gradle* Kotlin plugin's API instead,
 * and runs once, in the Gradle daemon, as part of evaluating `sample/build.gradle.kts` — it's
 * describing the plugin to Gradle, not implementing the plugin. Mixing the two dependency sets
 * into one module would mean every consumer's build script evaluation pulls in the full compiler
 * internals jar unnecessarily.
 *
 * ## The mavenLocal step
 *
 * [getPluginArtifact] below references `:compiler-plugin` purely by its Maven coordinates
 * (`group:compiler-plugin:version`), not a `project(":compiler-plugin")` dependency — Gradle's
 * subplugin mechanism resolves the compiler-plugin classpath as a normal dependency, and that
 * API only accepts coordinates, not project references. Since this whole build is local (never
 * published to a real repository), both `:compiler-plugin` and `:compiler-plugin-gradle` must be
 * published to `mavenLocal()` before `:sample` can resolve this plugin at all:
 * ```
 * ./gradlew :compiler-plugin:publishToMavenLocal :compiler-plugin-gradle:publishToMavenLocal
 * ```
 * See `.claude/references/compiler-plugins.md` for the full local dev workflow and why this
 * extra step exists.
 */
abstract class FieldValidatorGradlePlugin : KotlinCompilerPluginSupportPlugin {

    /** Nothing to configure eagerly — applying the plugin id is itself the signal Gradle needs. */
    override fun apply(target: Project) = Unit

    /** Every compilation (main and test alike) gets the plugin — `User` should validate in tests too. */
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    /** No configurable behavior yet, so no options are passed through to the compiler plugin. */
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider { emptyList() }
    }

    /**
     * An internal id `kotlinc` uses to key this plugin's own command-line options — distinct
     * from the Gradle plugin id above (`io.github.abdullahkhan118.fieldvalidator`, declared in
     * this module's `build.gradle.kts`). We have no options, so this string's exact value only
     * needs to be stable and unique, not meaningful to anyone.
     */
    override fun getCompilerPluginId(): String = "io.github.abdullahkhan118.fieldvalidator.compilerplugin"

    /** Where to fetch `:compiler-plugin`'s jar from — see the mavenLocal note above. */
    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "io.github.abdullahkhan118.fieldvalidator",
        artifactId = "compiler-plugin",
        version = "0.1.0",
    )
}
