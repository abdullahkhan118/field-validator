package io.github.abdullahkhan118.fieldvalidator.annotations.numeric.byte

/** Numeric range (inclusive on both ends): the annotated value must be in `[min, max]`. */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Range(val min: Byte = Byte.MIN_VALUE, val max: Byte = Byte.MAX_VALUE, val message: String = "")

/**
 * The annotated numeric value must be one of [values]. For `String` properties, use
 * [io.github.abdullahkhan118.fieldvalidator.annotations.string.Distinct].
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Distinct(val values: ByteArray, val message: String = "")
