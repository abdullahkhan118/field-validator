package io.github.abdullahkhan118.fieldvalidator.sample

import io.github.abdullahkhan118.fieldvalidator.annotations.Validated
import io.github.abdullahkhan118.fieldvalidator.annotations.numeric.byte.Distinct as ByteDistinct
import io.github.abdullahkhan118.fieldvalidator.annotations.numeric.byte.Range as ByteRange
import io.github.abdullahkhan118.fieldvalidator.annotations.numeric.double.Range as DoubleRange
import io.github.abdullahkhan118.fieldvalidator.annotations.numeric.float.Range as FloatRange
import io.github.abdullahkhan118.fieldvalidator.annotations.numeric.long.Distinct as LongDistinct
import io.github.abdullahkhan118.fieldvalidator.annotations.numeric.long.Range as LongRange
import io.github.abdullahkhan118.fieldvalidator.annotations.numeric.short.Range as ShortRange
import io.github.abdullahkhan118.fieldvalidator.runtime.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@Validated
data class NumericSample(
    @LongRange(min = 0L, max = 1_000_000L)
    @LongDistinct(values = [10L, 20L, 30L])
    val population: Long,

    @DoubleRange(min = 0.0, max = 1.0)
    val ratio: Double,

    @FloatRange(min = 0f, max = 100f)
    val percent: Float,

    @ShortRange(min = 0, max = 100)
    val score: Short,

    @ByteRange(min = 0, max = 10)
    @ByteDistinct(values = [1, 2, 3])
    val level: Byte,
)

class NumericConstraintsTest {

    @Test
    fun `valid values construct without throwing`() {
        NumericSample(population = 20L, ratio = 0.5, percent = 50f, score = 80, level = 2)
    }

    @Test
    fun `long distinct rejects values outside the allowed set`() {
        val exception = assertThrows(ValidationException::class.java) {
            NumericSample(population = 15L, ratio = 0.5, percent = 50f, score = 80, level = 2)
        }
        assertEquals("population", exception.result.errors.single().field)
    }

    @Test
    fun `byte range rejects out-of-range values`() {
        val exception = assertThrows(ValidationException::class.java) {
            NumericSample(population = 20L, ratio = 0.5, percent = 50f, score = 80, level = 20)
        }
        assertEquals("level", exception.result.errors.single().field)
    }
}
