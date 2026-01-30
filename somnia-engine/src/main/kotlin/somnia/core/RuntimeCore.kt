package somnia.core

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

// --- Security View ---

interface PrincipalView {
    fun isSystem(): Boolean
    fun roles(): Set<String>
    fun subject(): String
}

// --- Audit Interfaces ---

interface AuditRecorder {
    fun actionStart(action: String, permission: String)
    fun stepOk(action: String, binder: String, step: StepIR, ms: Long)
    fun stepFail(action: String, binder: String, step: StepIR, ms: Long, ex: Exception)
    fun actionOk(action: String)
    fun finish(): Audit
    fun stepNote(note: String, details: String)
}

@Component
class AuditFactory(private val store: AuditStore) {
    fun start(intent: String, plan: List<String>): AuditRecorder {
        return DefaultAuditRecorder(intent, plan)
    }
}

class DefaultAuditRecorder(val intent: String, val plan: List<String>) : AuditRecorder {
    private val steps = mutableListOf<AuditStep>()
    private val t0 = System.currentTimeMillis()

    override fun actionStart(action: String, permission: String) {}
    override fun stepOk(action: String, binder: String, step: StepIR, ms: Long) {
        steps.add(AuditStep(action, binder, "OK", ms))
    }
    override fun stepFail(action: String, binder: String, step: StepIR, ms: Long, ex: Exception) {
        steps.add(AuditStep(action, binder, "FAIL", ms, ex.message))
    }
    override fun actionOk(action: String) {}
    override fun stepNote(note: String, details: String) {}
    override fun finish(): Audit = Audit(
        java.util.UUID.randomUUID().toString(),
        intent,
        "COMPLETED",
        System.currentTimeMillis() - t0,
        steps
    )
}

// --- Execution State ---

class ExecutionState {
    var skipUntilAction: String? = null

    fun skipUntil(actionName: String) {
        this.skipUntilAction = actionName
    }

    fun shouldSkip(actionName: String): Boolean {
        val target = skipUntilAction ?: return false
        return target != actionName
    }

    fun clearSkipIfTarget(actionName: String) {
        if (skipUntilAction == actionName) {
            skipUntilAction = null
        }
    }

    fun isSkipping(): Boolean = skipUntilAction != null
}

data class ActionContext(
    val intent: String,
    val args: Map<String, Any?>,
    val body: Any?,
    val principal: PrincipalView,
    val results: MutableMap<String, Any?>,
    val audit: AuditRecorder,
    val eval: ExprEvaluator,
    val state: ExecutionState = ExecutionState()
) {
    fun result(actionName: String): Any? = results[actionName]
    fun setResult(actionName: String, value: Any?) { results[actionName] = value }
}

// --- Binder System ---

interface StepBinder {
    fun supports(step: StepIR): Boolean
    fun execute(step: StepIR, ctx: ActionContext): Any?
    fun name(): String = this.javaClass.simpleName
}

@Component
class BinderRegistry(val binders: List<StepBinder>) {
    fun binderFor(step: StepIR): StepBinder {
        return binders.find { it.supports(step) }
            ?: throw RuntimeException("No binder found for step: ${step.javaClass.simpleName}")
    }
}
