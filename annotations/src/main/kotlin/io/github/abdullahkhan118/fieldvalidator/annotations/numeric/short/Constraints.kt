package io.github.abdullahkhan118.fieldvalidator.annotations.numeric.short

/** Numeric range (inclusive on both ends): the annotated value must be in `[min, max]`. */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Range(val min: Short = Short.MIN_VALUE, val max: Short = Short.MAX_VALUE, val message: String = "")

/**
 * The annotated numeric value must be one of [values]. For `String` properties, use
 * [io.github.abdullahkhan118.fieldvalidator.annotations.string.Distinct].
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Distinct(val values: ShortArray, val message: String = "")
