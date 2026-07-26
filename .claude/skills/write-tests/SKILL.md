---
name: write-tests
description: Write JVM unit tests for field-validator — constraint annotations, the KSP processor's generated <ClassName>Validator objects, and runtime types (ValidationResult/ValidationError/ValidationException). Uses JUnit 5 (Jupiter), the only test stack in this repo. Invoke with a class name, file path, commit SHA, or annotation name.
---

# Write Tests

Generate tests for field-validator. There is exactly one kind of test in this repo: JVM unit tests run via `useJUnitPlatform()` in `sample/build.gradle.kts` — no Android instrumentation, no UI layer, no integration/e2e tiers.

## Inputs

- A class name, file path, or annotation name → write tests for that target.
- A commit SHA → write tests for every changed class in that commit.
- Nothing → use the latest commit: `git log -1 --format="%H"`.

## Choosing what to test

| Target | Where the test goes | What it exercises |
|---|---|---|
| A new/changed constraint annotation (`@Range`, `@Pattern`, `@Distinct`, etc.) | `sample/src/test/kotlin/.../<Feature>Test.kt` | The *generated* validator — annotations have no logic of their own, so testing them means testing the processor's emitted code end-to-end via a `@Validated` sample class |
| Processor logic (`ValidatorProcessor`) | Same — add/extend a sample data class to exercise the new codegen path | Generated `validate()` behavior for valid + invalid inputs |
| `runtime` types (`ValidationResult`, `ValidationError`, `ValidationException`) | `sample/src/test/kotlin/.../ValidationResultTest.kt` (or wherever existing runtime tests live) | Plain unit tests, no KSP involved |

You cannot unit-test the processor's KotlinPoet output directly without compiling generated code — this repo's existing pattern (`sample/src/test`) tests it *through* the generated `<ClassName>Validator`, which is the correct approach here. Don't attempt to invoke `ValidatorProcessor` directly in a test without a full KSP test harness (`kotlin-compile-testing`) — that's a bigger investment this repo hasn't taken on; flag it as a future option rather than improvising a partial mock.

## Stack

**JUnit 5 (Jupiter)** only — `sample/build.gradle.kts` declares `libs.junit.jupiter` + `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`, and `tasks.test { useJUnitPlatform() }`. Do not introduce JUnit 4, Kotest, MockK, or Mockito — there's nothing to mock here (no I/O, no framework dependencies to fake).

## Test skeleton

```kotlin
package io.github.abdullahkhan118.fieldvalidator.sample

import io.github.abdullahkhan118.fieldvalidator.runtime.ValidationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class UserValidatorTest {

    @Test
    fun `valid user passes with no errors`() {
        val user = User(name = "Ada Lovelace", email = "ada@example.com", age = 36, role = "ADMIN")
        val result = UserValidator.validate(user)
        assertTrue(result.isValid)
    }

    @Test
    fun `first violated constraint short-circuits remaining checks`() {
        val user = User(name = "1", email = "not-an-email", age = 200, role = "OWNER")
        val result = UserValidator.validate(user)
        assertEquals(1, result.errors.size)
        assertEquals("name", result.errors.single().field)
    }
}
```

Or, if the codebase calls `validate()` and expects a throw (check `runtime/Validation.kt` — `Validator<T>.validate` returns a `ValidationResult`, it does not throw; only `throwIfInvalid()` throws), assert on the returned `ValidationResult` directly rather than wrapping in `assertThrows` unless the test specifically calls `.throwIfInvalid()`.

## What to cover per constraint annotation

| Annotation | Cases |
|---|---|
| `Range` (per numeric type) | value at min, value at max, value below min, value above max, custom `message` override, nullable property with `null` value (must pass — null skips the check) |
| `Pattern` | matching string, non-matching string, custom `message`, nullable property with `null` (must pass) |
| `Distinct` (string or numeric) | value in the allowed set, value not in the set, custom `message`, nullable property with `null` (must pass) |

For every case above, also assert **fail-fast ordering**: when a class has multiple constraints, only the first violated one appears in `result.errors` (this repo's core design guarantee — see `ValidatorProcessor`'s docstring).

## Adding a new sample class for a new annotation

If no existing `@Validated` class exercises the annotation under test, add a small one next to `User.kt` (e.g. `sample/src/main/kotlin/.../<Feature>.kt`) rather than overloading `User` with unrelated fields:

```kotlin
@Validated
data class Widget(
    @field:Range(min = 0.0, max = 1.0)
    val ratio: Double,
)
```

## Compile and run

```bash
./gradlew :sample:kspKotlin          # regenerate <ClassName>Validator after annotation/sample changes
./gradlew :sample:test               # run all tests
./gradlew :sample:test --tests "io.github.abdullahkhan118.fieldvalidator.sample.UserValidatorTest"
```

`kspKotlin` must re-run before newly generated validator code is visible to the test compiler — a plain `./gradlew :sample:test` triggers this automatically via task dependencies, but if you're iterating quickly and something looks stale, run `kspKotlin` explicitly first.

## Report

Summarize:
- Files created or updated, number of tests added.
- Any new sample `@Validated` classes added and why.
- Any test failures pointing at a real processor bug (do not adjust the assertion to force a pass — fix the processor, or report it).
