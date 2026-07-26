---
name: kotlin-quality-gate
description: Kotlin/JVM quality gate for field-validator — reviews code correctness, KSP/KotlinPoet codegen safety, checks for unused symbols, enforces documentation and SOLID principles, enforces Kotlin idioms, checks test coverage, and writes missing unit tests. Use for changes to annotations/, runtime/, processor/, or sample/. Invoke with a class name, file path, or nothing (defaults to uncommitted + unstaged diff vs main).
---

# Kotlin Quality Gate

A quality gate for this repo's Kotlin/JVM modules: `annotations`, `runtime`, `processor`, `sample`. There is no Android layer here — skip anything that assumes ViewModels, Dispatchers.Main, or Android statics; the equivalent risk in this codebase is **generated-code correctness** (the processor emits Kotlin source that must compile and behave correctly for every valid `@Validated` class).

## Inputs

- A class name, file path, or fully-qualified name.
- Nothing → files changed vs `main`:
  ```bash
  git diff main...HEAD --name-only -- '*.kt'
  ```

## Stage Overview

```
Stage 1 — Kotlin Code Review         (correctness, idioms, codegen safety)  Blocking
Stage 2 — Unused Code Check          (dead symbols across the module)        Blocking
Stage 3 — Documentation Check        (KDoc completeness and staleness)       Blocking
Stage 4 — SOLID Principles           (SRP, OCP, LSP, ISP, DIP violations)   Blocking
Stage 5 — Write / Update Tests       (unit tests for any gap)                Non-blocking
Stage 6 — Optimization                                                       Advisory
Stage 7 — Run Tests                                                          Blocking
```

---

## Stage 1 — Kotlin Code Review

### Correctness

- `!!` on a value that could realistically be non-null-asserted incorrectly (e.g. `parameter.name!!` without checking) — flag High. The processor already handles this correctly with `parameter.name?.asString() ?: return@forEach`; any new code should follow that pattern.
- `try/catch` that silently swallows an exception — flag.
- A KSP symbol read (`.resolve()`, `.declaration`, annotation arguments) without accounting for `KSAnnotated.validate()` semantics — a symbol that fails `validate()` may have unresolvable references; don't assume every resolved type is safe to inspect further.
- Casting `KSValueArgument.value` (`as? String`, `as? List<Any>`) without a safe cast — an unexpected argument shape must degrade to `null`/empty, never throw inside the processor (a processor crash takes down the whole KSP round, not just one class).
- String-templated Kotlin source (`CodeBlock.builder().add(...)`) built by raw string interpolation instead of KotlinPoet's `%S`/`%T`/`%L` placeholders — raw interpolation risks unescaped quotes/identifiers producing invalid generated code. Flag any `"..."` literal embedded via `+` or `$` where a `%S`/`%T` placeholder should have been used instead.
- Annotation matching done on `annotation.shortName` instead of the resolved declaration's qualified name — breaks under import aliasing (see `ValidatorProcessor.emitPropertyChecks`, which deliberately resolves the declaration first). Flag any new annotation-dispatch code that reads `shortName` directly.
- Numeric literal rendering that doesn't account for `Short`/`Byte` having no Kotlin literal suffix — reuse `numberLiteral()` rather than reintroducing ad-hoc formatting.

### Safety

- `!!` on uncertain values (severity High).
- `as T` unsafe casts without a prior `is T` check, or without falling back to `null` via `as?`.
- Any generated-code path that could silently produce a `.kt` file that doesn't compile (e.g. an annotation argument default not handled, producing an empty/garbage expression) — the fix is to `return@forEach` (skip the check) rather than emit broken code.

### Kotlin idioms

- `if (x != null) x else default` → `x ?: default`.
- `list.filter { it.x }.map { it.y }` → `list.mapNotNull { ... }`.
- `when` as a statement that could be an expression.
- `mutableListOf()` returned as `List<T>` via explicit cast → `buildList { }`.
- Sequence vs. list churn: `classDecl.getDeclaredProperties()` and `annotations` are `Sequence`s in KSP — avoid needlessly collecting to `List` before a single terminal operation.

### Codegen-specific checks (this repo's equivalent of "coroutine patterns")

- **Declaration-order guarantees:** the processor walks `primaryConstructor.parameters` (not `getDeclaredProperties()`) specifically to preserve fail-fast ordering per README/docstring guarantees. Any refactor that switches the iteration source must re-verify order is preserved — check `sample/.../UserValidatorTest.kt` still asserts the first-violated-constraint-only behavior.
- **`Dependencies` correctness:** `generateValidator` builds `Dependencies(aggregating = false, containingFile)` keyed off the source file. A new codegen path must not use `aggregating = true` casually — it forces the file to regenerate on every module change, ballooning KSP round time.
- **Symbol return contract:** `process()` must return the `invalid` (unresolved) symbols so KSP retries them next round — silently dropping them makes affected classes fail to generate with no error surfaced. Flag anything that discards this return value or returns `emptyList()` unconditionally.

**Output on findings:**

```
[Stage 1 — FAILED]
file:line — description
  Severity: High | Medium | Low
  Detail: explanation
  Fix: recommendation

X issue(s). Resolve before continuing.
```

**If Stage 1 finds any High or Medium issues, stop.**

---

## Stage 2 — Unused Code Check (blocking)

```bash
# Private functions never called within their file
grep -n "private fun" processor/src/main/kotlin/**/*.kt

# Cross-module usage check for internal/public symbols
grep -rn "SymbolName" annotations/src runtime/src processor/src sample/src
```

- Unused `private`/`internal` functions, properties, or `const val`s.
- Unused imports.
- Dead constraint-annotation branches in `emitPropertyChecks`'s `when` (an annotation name matched but never reachable because no annotation declares that name).

**Do NOT flag:**
- Annotation classes themselves even if no sample currently uses them (numeric/{byte,short,float,double,long} constraints exist as public API surface, not dead code).
- `override fun validate` in generated/expected validator shape — required by the `Validator<T>` interface.

**If Stage 2 finds unused symbols, stop.**

---

## Stage 3 — Documentation Check (blocking)

Every public/internal symbol touched by the diff needs accurate KDoc — this repo's existing code (see `ValidatorProcessor.kt`) documents *why*, not *what* (e.g. explaining why `shortName` isn't used, why declaration order matters). Match that bar.

**Requires KDoc:**
- Every `class`, `object`, `interface`, `annotation class` that's public or internal.
- Every public/internal function — summary line, `@param` for non-obvious params, `@return` if non-obvious, `@throws` if it documents a thrown exception (e.g. `ValidationException`).
- Every constraint annotation needs a KDoc line stating its semantics and, where relevant, pointing to the sibling annotation in another package (see existing `Distinct` docs cross-referencing `numeric.*` vs `string`).

**Staleness:** flag KDoc that still describes a removed parameter, an outdated fail-fast/collect-all behavior claim, or a stale module list in `README.md` if the module set changes.

**Skip:** trivial one-line delegating functions with fully self-describing names; generated `<ClassName>Validator.kt` files (not hand-written, not reviewed for docs).

**If Stage 3 finds any documentation issues, stop.**

---

## Stage 4 — SOLID Principles (blocking)

See `solid-design-review` for the full pattern-level review. This stage checks the essentials inline:

- **SRP** — `ValidatorProcessor` mixes symbol resolution, per-annotation-type code emission, and literal rendering; new constraint types should extend the existing `when` in `emitPropertyChecks` rather than growing a second processor class. Flag any new class taking on both "parse KSP symbols" and "render CodeBlock" for a *different* concern than validation.
- **OCP** — new constraint kinds should be addable via a new `when` branch (or a table-driven mapping) without editing the shared literal/message helpers (`numberLiteral`, `stringSetLiteral`, `throwViolation`). Flag changes that special-case a new annotation deep inside a shared helper instead.
- **DIP** — the processor depends on `annotations`/`runtime` as an abstraction boundary (types, not implementations); flag any new code that hardcodes assumptions from a specific consumer module (e.g. `sample`-specific logic leaking into `processor`).

**If Stage 4 finds any violations, stop.**

---

## Stage 5 — Write / Update Unit Tests

### Test stack

| Library | Purpose |
|---|---|
| `junit:junit-jupiter` (JUnit 5) | Test runner (`useJUnitPlatform()` in `sample/build.gradle.kts`) |
| KSP compiling `sample` module | Exercises the real generated `<ClassName>Validator` |

Tests only exist meaningfully in `sample/src/test` today — that's the only module with a `@Validated` class wired through `ksp(project(":processor"))`. If a new constraint annotation needs coverage, add it to `sample/.../User.kt` (or a new sample data class) and assert against the generated validator, following `UserValidatorTest.kt` / `NumericConstraintsTest.kt`.

### What to cover per target

| Target | Cases |
|---|---|
| New constraint annotation | Passing value, boundary value, failing value, custom `message` override, nullable property behavior |
| Processor change (`ValidatorProcessor`) | Add/extend a sample class exercising the new codegen path; assert both the valid and first-fail-wins invalid case |
| `ValidationResult`/`ValidationError` (`runtime`) | `plus` combination, `isValid`, `throwIfInvalid()` |

### Skeleton (JUnit 5)

```kotlin
class UserValidatorTest {
    @Test
    fun `valid user passes`() {
        val user = User(name = "Ada Lovelace", email = "ada@example.com", age = 36, role = "ADMIN")
        val result = UserValidator.validate(user)
        assertTrue(result.isValid)
    }

    @Test
    fun `first violated constraint short-circuits the rest`() {
        val user = User(name = "1", email = "not-an-email", age = 200, role = "OWNER")
        val result = UserValidator.validate(user)
        assertEquals(1, result.errors.size)
        assertEquals("name", result.errors.single().field)
    }
}
```

### Compile after writing

```bash
./gradlew :sample:kspKotlin :sample:compileTestKotlin
```

**Output:**

```
[Stage 5 — Tests Written]
  sample/src/test/…/FooValidatorTest.kt — N test(s) added

Skipped: <symbol> — no @Validated sample class exercises it yet; add one to sample/User.kt or a new data class first.
```

---

## Stage 6 — Optimization (non-blocking, advisory)

- `Sequence` vs `List`: avoid `.toList()` calls on KSP sequences that are only iterated once.
- `Dependencies(aggregating = true)` anywhere — recompiles every generated file on any source change; prefer per-file `Dependencies`.
- Repeated `annotation.arguments.find { ... }` calls per annotation on hot paths — fine at current scale (compile-time, one pass per class) but flag if a change makes this O(n²) across many properties/annotations.
- KotlinPoet `ClassName(...)` literals repeated across the file instead of hoisted to a `private val` — minor, advisory only.

**Output is always advisory — never blocks.**

---

## Stage 7 — Run Tests

```bash
./gradlew :sample:test
```

**Output on pass:**

```
[Stage 7 — PASSED]
All tests green: N test(s) ran, 0 failures.
```

---

## Final Summary

```
Kotlin Quality Gate: PASSED ✓   (or FAILED ✗ — blocked at Stage N)
Stages: Code Review ✓ | Unused Code ✓ | Docs ✓ | SOLID ✓ | Tests ✓ | Optimization ✓ | Run ✓
```

## Test Naming Convention

Use backticked descriptive names:

```kotlin
fun `rejects age above max range`() { ... }
fun `distinct annotation accepts value in allowed set`() { ... }
```

## Common Pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| Generated validator missing for a class | `validate()` returned the class in `invalid` and it never resolves | Check the class doesn't depend on a type from another still-generating processor |
| Wrong annotation matched (e.g. int Range applied to a Long) | Matched on `shortName` instead of qualified name | Resolve `annotation.annotationType.resolve().declaration.qualifiedName` |
| `NullPointerException` on a nullable field's constraint check | `isNullable` branch not added to a new `when` case | Follow the `Range`/`Pattern`/`Distinct` pattern: null-safe `?.let { }` variant for nullable types |
| Test doesn't see newly generated code | `kspKotlin` not re-run before `compileTestKotlin` | `./gradlew :sample:kspKotlin` first, or just `./gradlew build` |
| KotlinPoet emits invalid Kotlin (unescaped string) | Used raw `"$value"` instead of `%S` in `CodeBlock` | Use `%S` for strings, `%T` for types, `%L` for literals/nested CodeBlocks |
