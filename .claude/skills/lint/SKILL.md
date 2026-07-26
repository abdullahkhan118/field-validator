---
name: lint
description: Kotlin style/lint check for field-validator. No ktlint/detekt is configured in this repo yet — this skill does a manual style pass against the conventions already established in the codebase (KDoc style, import discipline, KotlinPoet usage, naming), and gives setup steps for ktlint/detekt if the user wants automated enforcement. Use before pushing, when reviewing a diff, or when asked "does this lint clean" / "check style".
---

# Lint

## Current state of this repo

No lint tool is wired up — confirmed no `ktlint`/`detekt` plugin in any `build.gradle.kts`, no `.editorconfig`, no `detekt.yml`:

```bash
grep -rln "ktlint\|detekt" --include="*.gradle.kts" .
find . -iname ".editorconfig" -o -iname "detekt.yml" -not -path "./.gradle/*"
```

If those come back empty, there is no automated style gate — style is enforced only by matching the conventions already visible in the existing source. Don't claim "lint passed" without a tool; say "manual style check" instead.

## Manual style pass (until a tool exists)

Check the diff against conventions already established in this codebase:

### KDoc style
- Multi-sentence KDoc explaining **why**, not what (see `ValidatorProcessor.kt`'s class/function docs) — a comment restating the function name in prose is noise, not documentation. Match that bar for new public/internal symbols (see `kotlin-quality-gate` Stage 3 for the full documentation gate).
- One-line `/** ... */` for simple annotation classes (see `annotations/.../Constraints.kt`) — don't over-document trivial annotation parameters.

### Import discipline
- No wildcard imports (`import com.squareup.kotlinpoet.*`) — the existing code imports each KotlinPoet/KSP symbol explicitly, even when verbose (`com.squareup.kotlinpoet.ClassName`, `com.squareup.kotlinpoet.KModifier`, used fully-qualified in a few spots in `ValidatorProcessor.kt` rather than imported — that's an existing minor inconsistency; new code should prefer an explicit import over inline fully-qualified names, but don't rewrite unrelated lines to "fix" it in an unrelated diff).
- Unused imports — cross-check with `kotlin-quality-gate` Stage 2.

### Naming
- Constraint annotation classes: `PascalCase`, one word where possible (`Range`, `Pattern`, `Distinct`) — a new annotation introducing a multi-word name should have a clear reason (an existing single-word name is already taken in that package).
- Generated validator naming (`<ClassName>Validator`) is fixed by the processor — never hand-edit generated file naming conventions without updating `ValidatorProcessor.generateValidator`.
- Private helper functions in `ValidatorProcessor`: verb-first, single responsibility (`numberLiteral`, `stringSetLiteral`, `throwViolation`) — a new helper mixing two verbs (`buildAndValidate`) is a signal it's doing two things.

### Formatting
- 4-space indentation, trailing commas in multi-line constructor/function calls where the existing code already uses them (check `Range`'s `annotation class Range(val min: Int = ..., val max: Int = ..., val message: String = "")` — matches Kotlin's default `ktlint`/official style even without the tool installed).
- KotlinPoet `CodeBlock` construction: prefer `%S`/`%T`/`%L` placeholders over string interpolation for anything emitted as generated source (see `kotlin-quality-gate` Stage 1 — this is a correctness concern as much as a style one).

### Detect common issues quickly

```bash
grep -rn "^import .*\*$" annotations/src runtime/src processor/src sample/src   # wildcard imports
grep -rn "	" --include="*.kt" annotations/src runtime/src processor/src sample/src   # literal tabs
```

## If the user wants automated enforcement (bootstrap ktlint or detekt)

This is a deliberate setup step — confirm with the user before adding tooling and CI-shaping changes, then:

**ktlint (formatting-focused, lower ceremony):**
```kotlin
// root build.gradle.kts
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "<latest>"
}
```
Add the plugin + version to `gradle/libs.versions.toml` rather than inlining it (per `gradle-health` Check 1). Run via `./gradlew ktlintCheck` / `./gradlew ktlintFormat`.

**detekt (rule-based static analysis, more configurable):**
```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt") version "<latest>"
}
```
Add a `detekt.yml` at the repo root once rules need tuning beyond defaults. Run via `./gradlew detekt`.

Either integrates cleanly with this project's existing `allprojects {}` block in the root `build.gradle.kts`. Pick one, not both, to avoid conflicting formatting opinions — ktlint if the goal is just consistent formatting, detekt if the goal is catching complexity/style rules beyond formatting (e.g. flagging long functions, magic numbers in the processor's literal-rendering code).

## Output format (current, tool-less state)

```
[Lint — manual pass, no ktlint/detekt configured]
file:line — finding
  Convention: <the existing-code precedent this deviates from>
  Fix: <one line>

N finding(s). To get automated enforcement, see the bootstrap section above (confirm before I set it up).
```
