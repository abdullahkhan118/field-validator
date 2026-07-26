---
name: solid-design-review
description: SOLID-principles and design-pattern review for field-validator's Kotlin modules (annotations, runtime, processor, sample) — detects SRP/OCP/LSP/ISP/DIP violations with concrete evidence, identifies where a pattern (strategy table, visitor, builder) would genuinely simplify constraint-annotation handling vs. add ceremony, and flags pattern misuse. Use for class/feature-level design review or when the processor's annotation-dispatch code grows hard to extend. Invoke with a class name, file path, or nothing (diff vs main).
---

# SOLID & Design Pattern Review

Judgment-based review — every finding needs evidence from this codebase (a change that was awkward, duplicated dispatch logic, a helper that grew a special case), not principle recitation. "No pattern needed" is a valid verdict.

## SOLID checks — this repo's actual shape

### SRP — one reason to change
`ValidatorProcessor` currently has three responsibilities living together: (1) KSP symbol resolution/traversal, (2) per-constraint-annotation code emission, (3) literal/message rendering helpers. That split is *intentional and small enough to stay in one file* — flag only if a change adds a fourth concern (e.g. reading Gradle properties, doing I/O beyond `CodeGenerator`, or validating annotation arguments against some external schema) into the same class instead of a new collaborator.

### OCP — extend without editing
The `when (annotationName)` block in `emitPropertyChecks` is the repo's one deliberate type-dispatch point (see `Adding a new constraint annotation` in CLAUDE.md) — adding a case there is the *intended* extension mechanism, not a violation. Flag OCP only if:
- The same annotation-name dispatch is duplicated in a second place (e.g. a second `when` elsewhere matching the same annotation names) — extract a shared strategy instead.
- A new constraint type requires editing `numberLiteral`, `stringSetLiteral`, or `throwViolation` to special-case it, rather than composing them.

### LSP — substitutable subtypes
Only one interface in this repo — `Validator<T>`, implemented solely by generated code. Flag if a hand-written implementation of `Validator<T>` is ever added that returns `null` where `ValidationResult` is expected, or throws an exception type other than `ValidationException` for a failed check (breaks the contract `sample`/consumers rely on).

### ISP — no fat interfaces
Not currently a risk — `Validator<T>` has exactly one method. Flag if it grows unrelated methods (e.g. `validate` plus `describe()`/`schema()`) that most implementations wouldn't need; split into a separate interface instead.

### DIP — depend on abstractions where a seam is needed
- `processor` depends on `annotations` and `runtime` as data/type contracts, not implementations — correct as-is.
- Flag any new processor code that imports from `sample` (a consumer module reaching back into the library it consumes) — that's an inverted dependency and would also create a circular module reference.
- Flag hardcoded `ClassName(RUNTIME_PACKAGE, "...")` string literals proliferating beyond the existing few — if this grows, centralize as a small object of `ClassName` constants (not a full DI abstraction — this is compile-time codegen, not a runtime seam).

## Design-pattern checks

**Consider a pattern when its trigger is present:**
- The `when` in `emitPropertyChecks` growing past ~6-8 annotation cases with duplicated null-handling boilerplate → a small `ConstraintEmitter` strategy table (`Map<String, (KSAnnotation, propAccess, isNullable) -> CodeBlock>`) would remove the repetition. Not urgent at the current 3-case size (`Range`, `Pattern`, `Distinct`).
- Repeated "resolve declaration → get qualified name → compare" logic appearing in more than one place → extract a small helper function.

**Flag pattern misuse:**
- Introducing a builder/factory for `ValidationResult`/`ValidationError` — these are trivial data classes with named-arg constructors; a builder would be pure ceremony (`ValidationResult(errors = listOf(...))` is already idiomatic).
- Introducing a full visitor pattern over KSP symbols for what is currently a flat one-level annotation scan — premature given the processor handles one nesting level (constructor parameters), not a tree.
- Wrapping `Validator<T>` implementations in a registry/service-locator "for extensibility" — generated code is looked up by class name at the call site (`UserValidator.validate(user)`), which is simpler and matches the library's no-reflection design goal; don't add indirection that reintroduces reflection-like lookup.

## Detect (starting points, then read the class)

```bash
# Repeated annotation-name dispatch (OCP risk if duplicated)
grep -rn "when (annotationName)" processor/src/main/kotlin
grep -rn "annotationName ==" processor/src/main/kotlin

# Cross-module dependency direction (DIP/module-boundary risk)
grep -rn "import io.github.abdullahkhan118.fieldvalidator.sample" processor/src annotations/src runtime/src
```

## Output

```
[Principle/Pattern] <path>:<line> — finding
  Evidence: <the cost observed in this codebase>
  Refactor: <smallest concrete step; name the target shape>
  Effort: small (this PR) | medium (own ticket)
```

Max ~8 findings, ranked by cost-to-carry. Given this codebase's small size, most reviews should return few or zero findings — don't manufacture violations to fill a quota.
