package io.github.abdullahkhan118.fieldvalidator.sample

import io.github.abdullahkhan118.fieldvalidator.runtime.ValidationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() {
    val valid = User(name = "Ada Lovelace", email = "ada@example.com", age = 36, role = "ADMIN")
    println(Json.encodeToString(valid))
    println("  valid")

    try {
        User(name = "1", email = "not-an-email", age = 200, role = "OWNER")
    } catch (e: ValidationException) {
        // Fail-fast: only the first violated constraint ("name" fails @Pattern before
        // @Range or @Distinct are ever checked) is reported. No explicit validate() call
        // needed here — the compiler plugin injects it into User's own constructor.
        println("  ${e.result.errors.single().let { "${it.field}: ${it.message}" }}")
    }
}
