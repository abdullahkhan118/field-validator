package io.github.abdullahkhan118.fieldvalidator.sample

import io.github.abdullahkhan118.fieldvalidator.runtime.ValidationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() {
    val valid = User(name = "Ada Lovelace", email = "ada@example.com", age = -10, role = "ADMIN")
    println(Json.encodeToString(valid))
    UserValidator.validate(valid)
    println("  valid")

    val invalid = User(name = "1", email = "not-an-email", age = 200, role = "OWNER")
    try {
        UserValidator.validate(invalid)
    } catch (e: ValidationException) {
        // Fail-fast: only the first violated constraint ("name" fails @Pattern before
        // @Range or @Distinct are ever checked) is reported.
        println("  ${e.result.errors.single().let { "${it.field}: ${it.message}" }}")
    }
}
