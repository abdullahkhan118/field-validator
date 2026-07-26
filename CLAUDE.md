# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Kotlin compile-time field-validation library built with KSP (Kotlin Symbol Processing) —
no reflection at runtime. Annotate a class with `@Validated` and constrain its constructor
properties with numeric/string constraint annotations; the KSP processor generates a
`<ClassName>Validator` object with a `validate(target): ValidationResult` function at build time.

## Commands

```sh
./gradlew build              # build everything
./gradlew :sample:run        # run the sample Main.kt (shows a valid and invalid User)
./gradlew :sample:test       # run tests (JUnit 5 / Jupiter)
./gradlew test --tests "io.github.abdullahkhan118.fieldvalidator.sample.UserValidatorTest"
```

Tests live only in `sample/src/test` — that's the module that wires the KSP processor to a
real `@Validated` class, so it's the only place generated validators exist to test against.
After changing the processor, `:sample:kspKotlin` must re-run before tests reflect the change
(a plain `./gradlew build` does this automatically).

## Module structure and dependency flow

```
annotations  (the @Validated / @Range / @Distinct / @Pattern annotations, SOURCE retention only)
runtime      (ValidationResult, ValidationError, ValidationException, Validator<T> interface)
processor    (KSP SymbolProcessor: reads @Validated classes, emits <ClassName>Validator.kt)
sample       (applies annotations + ksp(processor); demonstrates generated output + tests it)
```

- `annotations` and `runtime` have no dependency on `processor` — they're the public API
  surface consumed by generated code and by user code.
- `processor` depends on `annotations` (to recognize annotation names) and `runtime` (the
  generated code references `ValidationResult`/`ValidationException`/`Validator`).
- `processor` is registered as a KSP provider via
  `processor/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`,
  pointing at `ValidatorProcessorProvider`.
- Only `sample` (or any future consumer module) applies the `com.google.devtools.ksp` plugin
  and adds `ksp(project(":processor"))` — `processor` itself is a plain Kotlin/JVM module, not
  a KSP-enabled one.

## How the processor works (`processor/.../ValidatorProcessor.kt`)

- Finds every symbol annotated `@Validated` per KSP round, splits into `valid`/`invalid` via
  `KSAnnotated.validate()`, generates code only for `valid`, and returns `invalid` so KSP
  retries them next round instead of dropping them.
- For each `@Validated` class, walks the **primary constructor parameters in declaration
  order** (not the properties list) and merges annotations from two sources per property:
  parameter-site annotations (default use-site target) and `@field:`/`@property:` annotations
  (visible only via `KSPropertyDeclaration.annotations`). Both are valid call-site syntax, so
  both must be checked.
- Annotation matching is done on the **resolved declaration's qualified name**, not
  `annotation.shortName` — call sites can alias imports (e.g. mixing
  `numeric.long.Distinct`/`numeric.int.Distinct` in the same file forces an alias), so relying
  on the literal written name would misidentify the annotation.
- Numeric constraint annotations exist per-type under `annotations.numeric.{int,long,double,
  float,short,byte}` (each package has its own `Range`/`Distinct` since Kotlin annotations
  can't be generic); string constraints (`Pattern`, `Distinct`) live under `annotations.string`.
  The processor distinguishes numeric vs. string `Distinct` by checking whether the resolved
  annotation's package starts with `annotations.string`.
- Generated `validate()` bodies are **fail-fast**: each constraint check is an
  `if (...) throw ValidationException(...)`, evaluated in annotation declaration order: the
  first violated constraint short-circuits the rest of that property's checks and every
  property after it. `ValidationResult.valid` is only returned if every check passes.
- Numeric literals need type-correct rendering since `Short`/`Byte` have no Kotlin literal
  suffix (`numberLiteral()` emits `5L`, `5f`, `(5).toShort()`, `(5).toByte()`, or plain `5`).

## Adding a new constraint annotation

1. Add the annotation under the appropriate `annotations` package (numeric type subpackage,
   or `annotations.string`), targeting `VALUE_PARAMETER, FIELD, PROPERTY` with `SOURCE` retention.
2. Add a `when` branch in `ValidatorProcessor.emitPropertyChecks` matching on the annotation's
   simple name (and package, if the name collides across numeric/string, as `Distinct` does).
3. If the check needs a custom message default, follow the existing pattern:
   `stringArg(annotation, "message")?.takeIf { it.isNotEmpty() } ?: "<default message>"`.
