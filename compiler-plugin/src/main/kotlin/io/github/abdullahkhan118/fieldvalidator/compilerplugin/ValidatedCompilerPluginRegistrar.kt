package io.github.abdullahkhan118.fieldvalidator.compilerplugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * The entry point `kotlinc` loads for this plugin, discovered via the
 * `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar` file —
 * the compiler-plugin equivalent of KSP's `SymbolProcessorProvider` service registration
 * (see `:processor`'s `ValidatorProcessorProvider`). Nothing else in the compiler ever calls
 * into this plugin except through this class.
 *
 * A [CompilerPluginRegistrar] can register several *kinds* of extension (frontend checkers,
 * IR lowering passes, etc.) — this plugin only needs one: [IrGenerationExtension], which lets
 * us walk and mutate the compiler's in-memory IR tree after resolution but before bytecode is
 * emitted. See [ValidatedIrGenerationExtension] for what it actually does.
 *
 * For the bigger picture — why a compiler plugin is even necessary here, how it composes with
 * the `:processor` KSP module, and the local mavenLocal-publish workflow this module's Gradle
 * setup requires — see `.claude/references/compiler-plugins.md`.
 */
@OptIn(ExperimentalCompilerApi::class)
class ValidatedCompilerPluginRegistrar : CompilerPluginRegistrar() {

    /**
     * `true` opts this plugin into the K2 frontend. We don't touch the frontend at all (no FIR
     * extensions registered below), so this only matters insofar as K2 requires plugins to
     * explicitly declare they're compatible with it rather than assuming K1 semantics.
     */
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(ValidatedIrGenerationExtension())
    }
}
