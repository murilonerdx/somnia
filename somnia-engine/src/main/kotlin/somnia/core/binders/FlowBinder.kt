package somnia.core.binders

import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import somnia.core.*

@Component
class FlowBinder(private val binders: ObjectProvider<BinderRegistry>) : StepBinder {
    override fun supports(step: StepIR): Boolean =
        step is StepIR.FlowIfNotNullStep || 
        step is StepIR.SetResultStep || 
        step is StepIR.SkipUntilStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        return when (step) {
            is StepIR.SetResultStep -> {
                val value = ctx.eval.eval(step.valueExpr, ctx)
                ctx.setResult(step.actionName, value)
                null
            }
            is StepIR.SkipUntilStep -> {
                ctx.state.skipUntil(step.actionName)
                null
            }
            is StepIR.FlowIfNotNullStep -> {
                val value = ctx.eval.eval(step.expr, ctx)
                if (value != null) {
                    for (nested in step.thenSteps) {
                        val binderRegistry = binders.ifAvailable ?: throw IllegalStateException("BinderRegistry not available")
                        val binder = binderRegistry.binderFor(nested)
                        binder.execute(nested, ctx)
                    }
                }
                null
            }
            else -> throw RuntimeException("Unsupported flow step: ${step.javaClass.simpleName}")
        }
    }
}
