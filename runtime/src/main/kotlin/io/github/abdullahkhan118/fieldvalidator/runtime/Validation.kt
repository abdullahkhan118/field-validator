package io.github.abdullahkhan118.fieldvalidator.runtime

import kotlinx.serialization.Serializable

@Serializable
data class ValidationError(
    val field: String,
    val message: String,
)

@Serializable
data class ValidationResult(
    val errors: List<ValidationError> = emptyList(),
) {
    val isValid: Boolean get() = errors.isEmpty()

    operator fun plus(other: ValidationResult): ValidationResult =
        ValidationResult(errors + other.errors)

    companion object {
        val valid = ValidationResult()
    }
}

class ValidationException(val result: ValidationResult) :
    IllegalArgumentException(result.errors.joinToString("; ") { "${it.field}: ${it.message}" })

fun ValidationResult.throwIfInvalid() {
    if (!isValid) throw ValidationException(this)
}

/**
 * Implemented by generated `<ClassName>Validator` objects.
 *
 * `validate` is fail-fast: it checks constraints in declaration order and throws
 * [ValidationException] on the *first* violation it finds, rather than collecting every
 * violation across all fields. It only returns normally (with an empty [ValidationResult])
 * when every constraint passes.
 */
interface Validator<T> {
    fun validate(target: T): ValidationResult

}
