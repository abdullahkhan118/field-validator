---
name: gradle-health
description: Gradle build health for field-validator — version catalog discipline (gradle/libs.versions.toml as single source of truth), KSP/KotlinPoet dependency wiring correctness across annotations/runtime/processor/sample, build-speed hygiene, and diagnosing slow or broken builds. Use when editing any build.gradle.kts/settings.gradle.kts/libs.versions.toml, when builds get slower, or before dependency upgrades.
---

# Gradle Health

This is a small 4-module Gradle Kotlin/JVM project (`annotations`, `runtime`, `processor`, `sample`) with no flavors, no build types beyond default, and no CI-specific env split — most Android-oriented gradle-health concerns (flavors, secure.properties, Firebase BoM) don't apply here. Focus on the checks below.

## Check 1 — Version catalog discipline

`gradle/libs.versions.toml` is the single source of truth for versions/coordinates. Every module's `build.gradle.kts` should reference `libs.*`, never inline a coordinate/version:

```bash
grep -rEn "(implementation|api|ksp|testImplementation)\(['\"]" annotations/build.gradle.kts runtime/build.gradle.kts processor/build.gradle.kts sample/build.gradle.kts
```

Any hit that isn't `project(":...")` or `libs.*` (or a plain string like `"org.junit.platform:junit-platform-launcher"` for the launcher, which has no catalog entry today — that's an accepted exception, not a violation) should move into `[libraries]`.

## Check 2 — KSP/plugin wiring correctness per module

- Only `sample` should apply `com.google.devtools.ksp` and declare `ksp(project(":processor"))` — `processor` itself must stay a plain `org.jetbrains.kotlin.jvm` module (it's the thing generating code, not consuming KSP itself). Flag if `com.google.devtools.ksp` is ever applied to `processor`, `annotations`, or `runtime`.
- `processor/build.gradle.kts` must depend on `libs.ksp.symbol.processing.api`, `libs.kotlinpoet`, `libs.kotlinpoet.ksp`, plus `project(":annotations")` and `project(":runtime")` — if a change removes any of these while the processor still references the corresponding APIs, the module fails to compile; check the diff explains why a dependency was removed.
- The processor is registered via `processor/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`. If `ValidatorProcessorProvider`'s fully-qualified name ever changes, this file's *contents* (not just its path) must be updated to match — a stale entry means KSP silently finds no processor and validators stop generating, with no compile error.

```bash
cat processor/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider
```

## Check 3 — api vs implementation

Every current inter-module dependency uses `implementation`, which is correct here — none of `annotations`/`runtime`'s types need to be exposed transitively beyond what each consumer explicitly imports. Flag any switch to `api` without a stated reason (e.g. `sample` needing to re-export a `runtime` type to its own consumers, which doesn't apply since `sample` is a leaf `application` module).

## Check 4 — jvmToolchain consistency

Every module pins `kotlin { jvmToolchain(17) }`. Flag a new/changed module that omits this or picks a different Java version — a mismatch across modules produces confusing "class file has wrong version" errors at link time, not at the mismatched module's own compile step.

## Check 5 — Diagnosing slow or broken builds

Order: `--stacktrace` for the real exception → `./gradlew :sample:dependencies` for version/resolution conflicts → `--refresh-dependencies` for corrupt caches. For "generated validator not found" style failures specifically, check `Check 2` above before assuming it's a general Gradle problem — the far more common cause in this repo is a KSP wiring or `META-INF/services` mismatch, not a Gradle cache issue.

```bash
./gradlew build --stacktrace
./gradlew :sample:dependencies --configuration kspClasspath
```

## Output

```
[Check N] <file>:<line> — finding
  Impact: <build correctness | build speed>
  Fix: <one line>
```
