package somnia.core

import org.springframework.stereotype.Component

@Component
class SomniaEngine(
    private val program: ProgramIR,
    private val interpreter: ActionInterpreter,
    private val auditFactory: AuditFactory,
    private val auditStore: AuditStore,
    private val eval: ExprEvaluator
) {
    fun run(intent: String, args: Map<String, Any?>, body: Any?, principal: PrincipalView): ExecResult {
        val plan = program.plansByIntent[intent] ?: throw RuntimeException("Unknown intent: $intent")
        
        val results = mutableMapOf<String, Any?>()
        val audit = auditFactory.start(intent, plan)
        val state = ExecutionState()
        val ctx = ActionContext(intent, args, body, principal, results, audit, eval, state)

        for (actionName in plan) {
            if (state.shouldSkip(actionName)) {
                // Future: audit.actionSkipped(actionName)
                continue
            }
            state.clearSkipIfTarget(actionName)

            val actionIR = program.actions[actionName] 
                ?: throw RuntimeException("Action meta missing for $actionName")
            
            val out = interpreter.execute(actionIR, ctx)
            if (out != null) {
                results[actionName] = out
            }
        }

        // Render response
        val render = program.renders[intent]
        val response = render?.let { eval.eval(it.expr, ctx) }

        val finalAudit = audit.finish()
        auditStore.save(finalAudit)

        return ExecResult(response, results, finalAudit)
    }
}

data class ExecResult(val response: Any?, val results: Map<String, Any?>, val audit: Audit)
