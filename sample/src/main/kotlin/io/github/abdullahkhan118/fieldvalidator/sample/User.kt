package io.github.abdullahkhan118.fieldvalidator.sample

import io.github.abdullahkhan118.fieldvalidator.annotations.Validated
import io.github.abdullahkhan118.fieldvalidator.annotations.numeric.int.Range
import io.github.abdullahkhan118.fieldvalidator.annotations.string.Distinct
import io.github.abdullahkhan118.fieldvalidator.annotations.string.Pattern
import kotlinx.serialization.Serializable

@Serializable
@Validated
data class User(
    @field:Pattern(regex = "[A-Za-z ]{2,32}")
    val name: String,

    @field:Pattern(regex = "[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    val email: String,

    @field:Range(min = 0, max = 150)
    val age: Int,

    @field:Distinct(values = ["ADMIN", "USER", "GUEST"])
    val role: String,
)
