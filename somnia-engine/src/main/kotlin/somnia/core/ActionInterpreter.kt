package somnia.core

import org.springframework.stereotype.Component
import somnia.spring.PermissionGate

@Component
class ActionInterpreter(
    internal val binders: BinderRegistry,
    private val gate: PermissionGate
) {
    fun execute(action: ActionIR, ctx: ActionContext): Any? {
        gate.check(action.permission, ctx.principal)
        ctx.audit.actionStart(action.name, action.permission)

        var lastResult: Any? = null
        for (step in action.steps) {
            lastResult = executeStep(action.name, step, ctx)
        }

        ctx.audit.actionOk(action.name)
        return lastResult
    }

    fun executeStep(actionName: String, step: StepIR, ctx: ActionContext): Any? {
        val t0 = System.currentTimeMillis()
        val binder = binders.binderFor(step)
        return try {
            val res = binder.execute(step, ctx)
            ctx.audit.stepOk(actionName, binder.name(), step, System.currentTimeMillis() - t0)
            res
        } catch (e: Exception) {
            ctx.audit.stepFail(actionName, binder.name(), step, System.currentTimeMillis() - t0, e)
            if (e is RuntimeException) throw e
            throw RuntimeException(e)
        }
    }
}
