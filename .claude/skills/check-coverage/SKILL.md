---
name: check-coverage
description: Check unit-test coverage for field-validator's sample/test suite. No JaCoCo or coverage tooling is configured in this repo yet — this skill verifies that against the current build files, reports what test coverage exists today by inspection, and gives the setup steps to bootstrap real coverage measurement (JaCoCo) if the user wants it. Use before pushing, when reviewing a PR, or when asked "did coverage drop" / "what's covered".
---

# Check Coverage

## Current state of this repo

There is **no coverage tool configured** — no `jacoco` plugin in `build.gradle.kts`, `sample/build.gradle.kts`, or any module, and no `.claude/coverage-baseline.json`. Before doing anything else, confirm this hasn't changed:

```bash
grep -rn "jacoco" --include=*.gradle.kts .
ls .claude/coverage-baseline.json 2>/dev/null || echo "no baseline file"
```

If that still comes back empty, there is no automated line-coverage number to compare against. Do not fabricate a percentage.

## What to do instead (until JaCoCo exists)

### Step 1 — Inventory what's tested by inspection

```bash
find sample/src/test -name "*.kt"
```

Cross-reference against the actual surface that needs coverage:
- Every constraint annotation in `annotations/src/main/kotlin/.../numeric/*/Constraints.kt` and `.../string/Constraints.kt` (`Range`, `Distinct`, `Pattern`) — is there a sample `@Validated` class + test exercising each one, for both the passing and failing case?
- `ValidatorProcessor`'s fail-fast ordering guarantee — is there a test with ≥2 violated constraints on one class asserting only the first is reported?
- `runtime/Validation.kt` — `ValidationResult.plus`, `isValid`, `ValidationResult.valid`, `throwIfInvalid()`, `ValidationException`'s message format.

Report gaps as a plain list — this is a manual gap analysis, not a computed percentage.

### Step 2 — Run what exists

```bash
./gradlew :sample:test
```

Confirm all current tests pass; a coverage check is meaningless if the underlying suite is red.

## If the user wants real coverage measurement (bootstrap JaCoCo)

This is a deliberate setup step, not something to do silently mid-review. Ask first, then:

1. Add to the root `build.gradle.kts` (or per-module):
   ```kotlin
   plugins {
       id("jacoco")
   }
   ```
2. Apply it to `sample` (the only module with tests) and configure a report task:
   ```kotlin
   tasks.jacocoTestReport {
       dependsOn(tasks.test)
       reports {
           xml.required.set(true)
       }
   }
   ```
3. Run `./gradlew :sample:jacocoTestReport` → produces `sample/build/reports/jacoco/test/jacocoTestReport.xml`.
4. Bootstrap `.claude/coverage-baseline.json` from that report's top-level `<counter type="LINE">` element (covered/missed), with `updatedOn` and `updatedFor` (commit SHA) fields — only after the user confirms today's numbers are the intended baseline.
5. On future runs, compare current `LINE` coverage to the stored baseline with a small tolerance (e.g. 0.10 percentage points) and warn on regression, same workflow as any JaCoCo-based check — see `.claude/skills/check-coverage` in other projects for the exact parsing script pattern if this gets set up.

## Output format (current, tool-less state)

```
[Coverage — inspection only, no JaCoCo configured]
Tested (sample/src/test):
  - Range (int): min/max/below/above ✓
  - Pattern: match/no-match ✓
  - Distinct (string): in-set/out-of-set ✓
  - Fail-fast ordering (multiple violations → first only) ✓

Gaps found:
  - Distinct (numeric, non-int types: long/double/float/short/byte) — no sample class exercises these
  - ValidationResult.plus — no direct test
  - Nullable-property null-skips-check behavior for Range/Pattern/Distinct — not covered

Suggested: add sample classes/tests for the gaps above (see write-tests skill).
To get an actual coverage percentage, configure JaCoCo (see "bootstrap" section) — confirm before I set it up.
```
