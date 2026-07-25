package io.github.abdullahkhan118.fieldvalidator.annotations.string

/**
 * The annotated `String` must be one of [values]. For numeric properties, use the `Distinct`
 * annotation in the matching `io.github.abdullahkhan118.fieldvalidator.annotations.numeric.*` package.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Distinct(val values: Array<String>, val message: String = "")

/** The annotated `String` must fully match [regex]. */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Pattern(val regex: String, val message: String = "")