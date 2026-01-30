package somnia.core.binders

import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import somnia.core.ActionContext
import somnia.core.ExprEvaluator
import somnia.core.StepBinder
import somnia.core.StepIR
import somnia.core.StepIR.RepoCallStep

@Component
class GenericRepoBinder(
    private val appCtx: ApplicationContext,
    private val evaluator: ExprEvaluator
) : StepBinder {

    override fun supports(step: StepIR): Boolean {
        return step is RepoCallStep
    }

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        if (step !is RepoCallStep) throw IllegalArgumentException("Unsupported step type")

        // 1. Resolve Repository Bean
        // Convention: repo name "TaskRepo" -> bean "taskRepo"
        val beanName = step.beanName.replaceFirstChar { it.lowercase() }
        val repoBean = appCtx.getBean(beanName)

        // 2. Resolve Arguments
        // step.args is List<ExprIR>
        val resolvedArgs: List<Any?> = step.args.map { expr -> evaluator.eval(expr, ctx) }

        // 3. Find Method via Reflection
        val method = repoBean::class.java.methods.find { 
            it.name == step.method && it.parameterCount == resolvedArgs.size
        } ?: throw IllegalStateException("Method ${step.method} with ${resolvedArgs.size} args not found in repo ${step.beanName}")

        // 4. Invoke Method
        val result = method.invoke(repoBean, *resolvedArgs.toTypedArray())

        // 5. Store Result
        ctx.setResult("lastRepoResult", result)
        return result
    }
}
