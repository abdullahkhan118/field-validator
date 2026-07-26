# Compiler plugins in this project

This document explains `:compiler-plugin` and `:compiler-plugin-gradle` — the two modules that
make `User(...)` construction automatically throw on invalid data, with zero boilerplate in
`User.kt` itself. If you're touching either module, or wondering why they exist instead of just
extending `:processor` (the KSP module), read this first.

## The problem these modules solve

`:processor` (a KSP `SymbolProcessor`) reads the `@Range`/`@Pattern`/`@Distinct`/etc. annotations
on a `@Validated` class and generates a `<ClassName>Validator` object with a `validate` function
that throws on the first violated constraint. That part works well — but KSP is **strictly
additive**: it can only generate brand-new files, never modify the class it read the annotations
from. So on its own, KSP gets you exactly one file (`UserValidator.kt`) and nothing that actually
*calls* it — every caller would need to remember to write `UserValidator.validate(user)` after
constructing a `User`, or add `init { UserValidator.validate(this) }` to `User` itself by hand.
Forgettable, and nothing catches the omission at compile time.

`:compiler-plugin` closes that gap: it's a real Kotlin compiler plugin that finds every
`@Validated` class and mutates its primary constructor to append a call to
`<ClassName>Validator.validate(this)` — automatically, with no line for you to remember to write.

## Why this needed a compiler plugin instead of "just KSP"

We went through this reasoning at length before building it; the short version:

| Approach | Can it modify `User`'s own body? |
|---|---|
| KSP | No — additive only, by design (same restriction Java's APT and KAPT have) |
| A spec/interface (`UserSpec` read by KSP, `User` fully generated) | Technically yes, but `User` stops being something you write by hand, and anyone typing a parameter as `UserSpec` loses `copy()`/`componentN()`/destructuring/etc. — a real footgun. Rejected for that reason. |
| Post-compile bytecode weaving (ASM/ByteBuddy) | Yes, but at the `.class` level — needs annotations to have `BINARY`/`RUNTIME` retention (ours are `SOURCE`), and is generally low-level/fragile to maintain. |
| **A real compiler plugin (FIR/IR)** | **Yes** — an `IrGenerationExtension` runs after full resolution but before bytecode emission, directly on the compiler's in-memory IR tree for the actual class. This is what we built. |

## The two-module split, and why it's two modules

- **`:compiler-plugin`** — the actual plugin `kotlinc` loads. Depends on
  `kotlin-compiler-embeddable` (`compileOnly` — it's provided by the compiler at runtime, never
  bundled into our jar). Contains:
  - `ValidatedCompilerPluginRegistrar` — the `META-INF/services`-discovered entry point (same
    idea as `:processor`'s `ValidatorProcessorProvider`, different registration interface).
  - `ValidatedIrGenerationExtension` — the actual IR mutation logic. Read its KDoc first; it's
    the part worth understanding deeply if you're extending this.

- **`:compiler-plugin-gradle`** — a *Gradle* plugin (`KotlinCompilerPluginSupportPlugin`) that
  tells Gradle's Kotlin support "add `:compiler-plugin`'s jar to `kotlinc`'s plugin classpath for
  this module." It depends on `kotlin-gradle-plugin-api`, not `kotlin-compiler-embeddable` — it
  runs in the Gradle daemon while evaluating build scripts, not inside the compiler. Keeping it
  separate means consuming a compiler plugin doesn't drag the (large) compiler-internals jar into
  every module's build-script classpath.

This mirrors how `kotlinx.serialization`, `kotlin-parcelize`, and every other real Kotlin compiler
plugin is structured — it's not an idiosyncrasy of this project.

## What `ValidatedIrGenerationExtension` actually does

In short: for every `@Validated` class, find its `<ClassName>Validator` (already compiled by the
time this runs — KSP's `kspKotlin` task feeds its generated sources into this same
`compileKotlin` invocation's source set, before IR generation happens), and append one IR
statement — `<ClassName>Validator.validate(this)` — to the end of its primary constructor body.
That position is exactly where a hand-written trailing `init { ... }` block's statements would
sit, since Kotlin lowers `init` blocks and constructor-parameter property assignments into the
primary constructor's body in declaration order.

It deliberately does **not** reimplement any constraint-checking logic. `:processor` owns "what
the checks are"; this plugin owns "make sure they run." Don't blur that line — if you're tempted
to add IR-level checks here, that logic almost certainly belongs in `:processor` instead, with
this plugin staying a thin "make sure `validate()` gets called" layer.

### If you need to extend the IR logic

The whole implementation lives in `ValidatedIrGenerationExtension.injectValidationCall`. Common
things you might want to change:

- **Different injection point** (e.g. run validation *first* instead of last): move where
  `body.statements.add(validateCall)` inserts relative to the existing statements — `add(0, ...)`
  for "run before anything else," though note the constructor parameters aren't yet assigned to
  fields at that point if you go that route.
- **Skip certain classes**: add a condition inside `processContainer`'s `if (irClass.hasAnnotation(...))`
  check.
- **Support validating a different function shape**: the lookup of the `validate` function by
  name (`"validate"`) and single value-parameter assumption both live in `injectValidationCall` —
  update both if `:processor`'s generated `Validator` interface shape ever changes.

## The local mavenLocal workflow (read this before your build fails mysteriously)

Gradle's subplugin mechanism (`KotlinCompilerPluginSupportPlugin.getPluginArtifact()`) resolves
the compiler-plugin jar as a normal dependency coordinate (`group:artifact:version`) — it has no
concept of `project(":compiler-plugin")`. Since this project has never been published anywhere
real, both `:compiler-plugin` and `:compiler-plugin-gradle` must be published to your local Maven
repository (`~/.m2`) before `:sample` (or anything else applying
`id("io.github.abdullahkhan118.fieldvalidator")`) can resolve them:

```sh
./gradlew --configure-on-demand :compiler-plugin:publishToMavenLocal :compiler-plugin-gradle:publishToMavenLocal
```

**Why `--configure-on-demand` is required here, specifically**: without it, Gradle configures
*every* project's build script before running *any* task — including `sample/build.gradle.kts`,
which references the not-yet-published plugin id and fails immediately, even though you only
asked to run tasks in `:compiler-plugin`/`:compiler-plugin-gradle`. `--configure-on-demand` tells
Gradle to only configure the projects the requested tasks actually need, sidestepping the
chicken-and-egg entirely.

You only need to re-run the publish step when you change code in `:compiler-plugin` or
`:compiler-plugin-gradle` themselves — everyday work in `:annotations`/`:processor`/`:sample`
doesn't require it.

## Gotchas hit building this (so you don't have to rediscover them)

- **`ClassId` lives in `org.jetbrains.kotlin.name`**, not `org.jetbrains.kotlin.ir` — easy to
  guess wrong.
- **`DeclarationIrBuilder` lives in `org.jetbrains.kotlin.backend.common.lower`**, not
  `org.jetbrains.kotlin.ir.builders` (that package holds the free functions like `irCall`/`irGet`/
  `irGetObject`, but not the builder class itself).
- If you're unsure where a compiler-internal class lives, don't guess — unzip the resolved
  `kotlin-compiler-embeddable` jar and grep it:
  ```sh
  JAR=$(find ~/.gradle/caches -iname "kotlin-compiler-embeddable-*.jar" | head -1)
  unzip -l "$JAR" | grep -E "/YourClassName\.class$"
  ```
- **`CompilerPluginRegistrar` and its `supportsK2`/`registerExtensions` require
  `@OptIn(ExperimentalCompilerApi::class)`** — this API genuinely has no stability guarantee
  across Kotlin versions; a Kotlin version bump may require adjustments here.
- **`IrClass.declarations` and `IrClassSymbol.owner` require
  `@OptIn(UnsafeDuringIrConstructionAPI::class)`** — safe for us specifically because
  `IrGenerationExtension` runs after the whole module's IR is fully built (see the KDoc on
  `ValidatedIrGenerationExtension` for the fuller reasoning); don't assume that opt-in is
  automatically safe in a different kind of extension that might run *during* IR construction.
- **`compiler-plugin-gradle` needs `-Xskip-metadata-version-check`.** `java-gradle-plugin`
  implicitly depends on `gradleApi()`, which bundles whatever Kotlin stdlib/reflect ships with
  your installed Gradle distribution — on Gradle 9.4 that's newer (metadata version 2.3.0) than
  this project's own Kotlin (2.0.21, which only reads up to metadata version 2.1.0) without that
  flag. If you bump the Gradle wrapper/distribution version, you may need to revisit whether this
  flag is still necessary.
- **A version is required on the plugin id in `sample/build.gradle.kts`**
  (`id("io.github.abdullahkhan118.fieldvalidator") version "0.1.0"`), even though the module's
  `group`/`version` are already set via the root `build.gradle.kts`'s `allprojects` block — Gradle
  only waives the version requirement for plugins resolved from an *included build*, not a plugin
  repository (which is what `mavenLocal()` is, here).

## Debugging tip: seeing the injected code

There's no KotlinPoet-style textual output to read for this plugin the way there is for
`:processor`'s generated `.kt` files — IR is a binary tree, not text. The most direct sanity check
is behavioral: construct an invalid instance of a `@Validated` class and confirm it throws with no
explicit `validate()` call anywhere in the calling code (see
`sample/src/main/kotlin/.../Main.kt` and `UserValidatorTest.kt` for exactly this pattern).

To confirm at the bytecode level, `javap -c -p` on the compiled `User.class` shows the injected
call plainly, right after the field assignments and before the constructor returns:

```
public io.github...sample.User(java.lang.String, java.lang.String, int, java.lang.String);
  Code:
     ...
     23: aload_0
     24: aload_1
     25: putfield      #25    // Field name:Ljava/lang/String;
     ...                      // (the rest of the constructor-parameter field assignments)
     44: getstatic     #39    // Field .../UserValidator.INSTANCE:Lio/.../UserValidator;
     47: aload_0
     48: invokevirtual #43    // Method .../UserValidator.validate:(Lio/.../User;)L.../ValidationResult;
     51: pop
     52: return
```

That's exactly the shape this plugin is meant to produce: `UserValidator.validate(this)`, called
once every constructor-parameter field has been assigned, with the return value discarded (`pop`)
since the constructor doesn't need it — `validate` communicates failure via throwing, not via its
return value.
