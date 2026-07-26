---
name: modularization-review
description: Module-boundary review for field-validator (:annotations, :runtime, :processor, :sample) — dependency direction, what belongs in which module, when a new module is warranted, and build-impact of boundary violations. Use when adding a module, moving code between modules, reviewing a change that touches more than one module, or when a new dependency edge is introduced.
---

# Modularization Review

## Current graph

```
:annotations  — annotation classes only (SOURCE retention, no logic). No project dependencies.
:runtime      — ValidationResult / ValidationError / ValidationException / Validator<T>. No project dependencies.
:processor    — KSP SymbolProcessor. Depends on :annotations, :runtime.
:sample       — applies KSP + the processor; example @Validated class + tests. Depends on :annotations, :runtime, ksp(:processor).
```

`:annotations` and `:runtime` are the public API surface — anything depending on them is a consumer of the library, not part of its internals. `:processor` is implementation detail that a real consumer never imports directly (only via `ksp(...)`, a build-time-only edge, not a compile classpath edge).

## Checklist

### 1. Dependency direction
- `:annotations` and `:runtime` must never depend on `:processor` or `:sample` — they are the leaves of the graph. Flag immediately if either gains a `project(...)` dependency.
- `:processor` must never depend on `:sample` (a library depending on its own example consumer is inverted and would also create a real Gradle cycle once `:sample` adds `ksp(project(":processor"))`).
- `:processor`'s dependency on `:annotations` should stay read-only usage (matching annotation names/packages) — it must not execute code from `:annotations` (there isn't any to execute; keep it that way. `:annotations` should never gain executable logic — see Placement below).

```bash
grep -rn "project(\":processor\")\|project(\":sample\")" annotations/build.gradle.kts runtime/build.gradle.kts
```

### 2. API surface
- Every `public` symbol in `:annotations`/`:runtime` is permanent library API once published — check the `README.md` module list stays accurate whenever a new public annotation or runtime type is added. Anything in `:processor` that isn't `ValidatorProcessor`/`ValidatorProcessorProvider` should default to `internal` (or `private`), since nothing outside the module should reference `:processor` internals directly.

### 3. Placement
- New constraint annotations → `:annotations` (matching numeric-subpackage or `.string`, no logic — annotations here are pure metadata, SOURCE retention).
- New runtime types consumed by generated code (e.g. a new result/error shape) → `:runtime`.
- New codegen logic (new `when` branches, new emission helpers) → `:processor`.
- New example/demonstration code or new tests → `:sample`.
- Flag: any KotlinPoet/KSP import appearing in `:annotations` or `:runtime` (those modules should have zero build-time-codegen dependencies — they're consumed at compile time by generated code, not participants in generating it).

### 4. When to extract a new module
Only worth it when ≥2 of: the code has a distinct consumer with its own release cadence (e.g. a future Gradle-plugin wrapper around `:processor`, or a multiplatform variant of `:runtime`); build-time isolation would measurably help; the boundary is already clean with no shared mutable state. At this project's size (4 small modules, one KSP round per build), don't recommend splitting further without one of those triggers — say so explicitly if asked.

### 5. Version catalog
Same rule as `gradle-health` Check 1 — all coordinates via `gradle/libs.versions.toml`, no hardcoded versions in a module's `build.gradle.kts`.

## Detect

```bash
grep -n "project(" annotations/build.gradle.kts runtime/build.gradle.kts processor/build.gradle.kts sample/build.gradle.kts
git diff main...HEAD --name-only | cut -d/ -f1 | sort -u   # modules touched by this change
```

## Output

```
[Direction/Placement/API] <path> — finding
  Cost: <what this couples or would break if published>
  Move: <where the code/dependency belongs>
```

End with a verdict on the module graph impact of the change (none / additive / boundary-eroding).
