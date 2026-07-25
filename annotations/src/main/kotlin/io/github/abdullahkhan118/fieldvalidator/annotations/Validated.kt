package io.github.abdullahkhan118.fieldvalidator.annotations

/**
 * Marks a class as eligible for validator generation.
 * The KSP processor emits a `<ClassName>Validator` with a `validate(target): ValidationResult` function.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Validated
