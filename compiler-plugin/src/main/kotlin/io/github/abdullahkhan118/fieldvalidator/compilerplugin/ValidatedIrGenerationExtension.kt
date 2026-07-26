package io.github.abdullahkhan118.fieldvalidator.compilerplugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** The `@Validated` annotation's fully-qualified name — matched by string, same as `:processor`. */
private const val VALIDATED_FQN = "io.github.abdullahkhan118.fieldvalidator.annotations.Validated"

/**
 * For every class annotated `@Validated`, appends a call to `<ClassName>Validator.validate(this)`
 * at the end of its primary constructor — i.e. exactly where a hand-written trailing
 * `init { <ClassName>Validator.validate(this) }` block would run.
 *
 * ## Why this exists
 *
 * KSP (see `:processor`) already generates `<ClassName>Validator` for every `@Validated` class,
 * but KSP is strictly additive: it can generate *new* files, never modify the class it's reading
 * annotations from. So without this plugin, callers have to remember to write that one `init`
 * line themselves in every `@Validated` class, or call `XValidator.validate(x)` manually after
 * constructing `x` — easy to forget, and nothing catches the omission at compile time.
 *
 * This plugin closes that gap: it runs *after* KSP's generated `<ClassName>Validator` sources
 * have already been compiled into this same module (KSP's `kspKotlin` task runs as a distinct
 * earlier step that feeds its output into the main `compileKotlin` task's source set — by the
 * time this extension's [generate] runs, `UserValidator` is just another already-resolved class
 * in [IrPluginContext], no different from one you wrote by hand), and mutates `User`'s own
 * constructor to call it.
 *
 * ## Why IR, not KSP, and not FIR
 *
 * KSP cannot mutate an existing declaration's body — see above. FIR (the frontend) resolves
 * *types*, not executable code — it's how you'd make a *synthetic function* type-check as if it
 * existed, not how you'd add a *statement* to a real constructor's body. [IrGenerationExtension]
 * is the one extension point that runs after full resolution but before bytecode emission, with
 * direct access to mutate the compiler's actual in-memory IR tree — including inserting new
 * statements into an existing constructor's body. See `.claude/references/compiler-plugins.md`
 * for the fuller comparison across KSP / FIR / IR / bytecode-weaving approaches, and why this
 * project ended up here.
 *
 * ## What this deliberately does NOT do
 *
 * It does not reproduce any of `:processor`'s constraint-checking logic (the `@Range`/`@Pattern`/
 * `@Distinct` conditionals) in IR. That logic already exists, correctly, in the generated
 * `<ClassName>Validator.validate` function — this plugin's only job is to make sure that
 * function actually gets *called*. Keeping the two concerns separate (KSP owns "what the checks
 * are"; this plugin owns "make sure they run") means the two modules can be reasoned about, and
 * modified, independently.
 *
 * ## About the `@OptIn(UnsafeDuringIrConstructionAPI::class)`
 *
 * That opt-in guards `IrClass.declarations` and `IrClassSymbol.owner` — access that's genuinely
 * unsafe *while the IR tree for a class is still being built* (declarations can still be mutated
 * out from under you mid-construction). [IrGenerationExtension] runs after the entire module's
 * IR has been fully built, so every class this plugin touches is already complete; the opt-in
 * here is acknowledging a real but inapplicable-to-us hazard, not suppressing an actual risk.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class ValidatedIrGenerationExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.files.forEach { file -> processContainer(file, pluginContext) }
    }

    /**
     * Recurses into every class declared directly in [container] (an [IrModuleFragment]'s file,
     * or a class nested inside one) so that `@Validated` nested/inner classes are handled too,
     * not just top-level ones.
     */
    private fun processContainer(container: IrDeclarationContainer, pluginContext: IrPluginContext) {
        container.declarations.filterIsInstance<IrClass>().forEach { irClass ->
            if (irClass.hasAnnotation(FqName(VALIDATED_FQN))) {
                injectValidationCall(irClass, pluginContext)
            }
            // A @Validated class is very unlikely to itself contain nested @Validated classes,
            // but recursing costs nothing and keeps this correct if it ever does.
            processContainer(irClass, pluginContext)
        }
    }

    /**
     * Appends `<ClassName>Validator.validate(this)` as the last statement of [irClass]'s
     * primary constructor body.
     *
     * Silently does nothing if [irClass] has no primary constructor or `thisReceiver`, or if
     * the matching `<ClassName>Validator` object (or its `validate` function) can't be
     * resolved — all of which would only happen if `@Validated` were misused on a class KSP
     * itself also can't generate a validator for (e.g. an interface), which is a KSP-time
     * concern, not something this plugin should also report a confusing, redundant error for.
     */
    private fun injectValidationCall(irClass: IrClass, pluginContext: IrPluginContext) {
        val constructor = irClass.primaryConstructor ?: return
        val body = constructor.body as? IrBlockBody ?: return
        val thisReceiver = irClass.thisReceiver ?: return

        // `UserValidator` lives in the same package as `User` — derive its ClassId the same
        // way :processor's ValidatorProcessor derives the generated file's name/package.
        val validatorClassId = ClassId(
            irClass.kotlinFqName.parent(),
            Name.identifier("${irClass.name.asString()}Validator"),
        )
        val validatorClassSymbol = pluginContext.referenceClass(validatorClassId) ?: return
        val validateFunctionSymbol = validatorClassSymbol.owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .singleOrNull { it.name.asString() == "validate" }
            ?.symbol
            ?: return

        // DeclarationIrBuilder builds IR nodes "in the scope of" the given symbol — here, the
        // constructor we're about to append a statement into.
        val builder = DeclarationIrBuilder(pluginContext, constructor.symbol)
        val validateCall = builder.irCall(validateFunctionSymbol).apply {
            // `UserValidator` is a Kotlin `object`; calling one of its member functions means
            // the call's dispatch receiver is the object's singleton instance, not `this`.
            dispatchReceiver = builder.irGetObject(validatorClassSymbol)
            // The single `target: User` parameter of `validate` is `this` — the instance whose
            // constructor we're currently inside.
            putValueArgument(0, builder.irGet(thisReceiver))
        }

        body.statements.add(validateCall)
    }
}
