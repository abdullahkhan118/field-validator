package io.github.abdullahkhan118.fieldvalidator.annotations.numeric.double

/**
 * Numeric range (inclusive on both ends): the annotated value must be in `[min, max]`.
 *
 * Note: `Double.MIN_VALUE` is the smallest positive nonzero value, not the most negative one —
 * pass an explicit `min` if you need a true lower bound below zero.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Range(val min: Double = Double.MIN_VALUE, val max: Double = Double.MAX_VALUE, val message: String = "")

/**
 * The annotated numeric value must be one of [values]. For `String` properties, use
 * [io.github.abdullahkhan118.fieldvalidator.annotations.string.Distinct].
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Distinct(val values: DoubleArray, val message: String = "")
