# field-validator

A Kotlin field-validation library in the spirit of `javax.validation`, built with **KSP**
(Kotlin Symbol Processing) for compile-time code generation and **kotlinx.serialization**
for model (de)serialization.

Annotate a class with `@Validated` and constrain its properties with `@NotNull`, `@NotBlank`,
`@Size`, `@Min`, `@Max`, `@Email`, or `@Pattern`. The KSP processor generates a
`<ClassName>Validator` object with a `validate(target)` function — no reflection at runtime.

## Modules

- `annotations` — the validation annotations (`@Validated`, `@NotBlank`, `@Size`, ...)
- `runtime` — `ValidationResult` / `ValidationError` types consumed by generated validators
- `processor` — the KSP `SymbolProcessor` that generates `<ClassName>Validator` objects
- `sample` — example usage with a `kotlinx.serialization`-annotated data class

## Usage

```kotlin
@Serializable
@Validated
data class User(
    @NotBlank
    @Size(min = 2, max = 32)
    val name: String,

    @Email
    val email: String,

    @Min(0)
    @Max(150)
    val age: Int,
)

val result = UserValidator.validate(user)
if (!result.isValid) {
    result.errors.forEach { println("${it.field}: ${it.message}") }
}
```

## Build & run

```sh
./gradlew build
./gradlew :sample:run
```
