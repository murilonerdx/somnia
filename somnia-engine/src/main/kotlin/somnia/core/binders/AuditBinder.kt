package somnia.core.binders

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import somnia.core.*

@Component
class AuditBinder(
    private val store: AuditStore,
    private val om: ObjectMapper
) : StepBinder {
    override fun supports(step: StepIR): Boolean = step is StepIR.AuditFetchStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val s = step as StepIR.AuditFetchStep
        val runId = ctx.eval.eval(s.runIdExpr, ctx).toString()
        val audit = store.get(runId) ?: throw RuntimeException(s.orFailError ?: "Audit $runId not found")
        return om.valueToTree<com.fasterxml.jackson.databind.JsonNode>(audit)
    }
}
