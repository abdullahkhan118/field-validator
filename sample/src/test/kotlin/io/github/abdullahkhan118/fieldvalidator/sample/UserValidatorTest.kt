package io.github.abdullahkhan118.fieldvalidator.sample

import io.github.abdullahkhan118.fieldvalidator.runtime.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UserValidatorTest {

    @Test
    fun `valid user constructs without throwing`() {
        User(name = "Ada Lovelace", email = "ada@example.com", age = 36, role = "ADMIN")
    }

    @Test
    fun `invalid user throws on first violated constraint only`() {
        val exception = assertThrows(ValidationException::class.java) {
            User(name = "1", email = "not-an-email", age = 200, role = "OWNER")
        }
        val error = exception.result.errors.single()
        assertEquals("name", error.field)
    }

    @Test
    fun `later fields still fail once earlier ones are fixed`() {
        val exception = assertThrows(ValidationException::class.java) {
            User(name = "Ada Lovelace", email = "not-an-email", age = 200, role = "OWNER")
        }
        val error = exception.result.errors.single()
        assertEquals("email", error.field)
    }

    @Test
    fun `distinct string constraint rejects values outside the allowed set`() {
        val exception = assertThrows(ValidationException::class.java) {
            User(name = "Ada Lovelace", email = "ada@example.com", age = 36, role = "OWNER")
        }
        val error = exception.result.errors.single()
        assertEquals("role", error.field)
    }
}
