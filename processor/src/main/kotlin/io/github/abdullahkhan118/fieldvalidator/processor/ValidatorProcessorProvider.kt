package io.github.abdullahkhan118.fieldvalidator.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * KSP entry point, discovered via the `META-INF/services` registration.
 * Constructs the [ValidatorProcessor] that performs the actual code generation.
 */
class ValidatorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ValidatorProcessor(environment.codeGenerator, environment.logger)
}
