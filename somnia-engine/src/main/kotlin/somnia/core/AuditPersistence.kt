package somnia.core

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

interface AuditStore {
    fun save(audit: Audit)
    fun get(runId: String): Audit?
}

@Component
class InMemoryAuditStore : AuditStore {
    private val db = ConcurrentHashMap<String, Audit>()

    override fun save(audit: Audit) {
        db[audit.runId] = audit
    }

    override fun get(runId: String): Audit? = db[runId]
}

data class Audit(
    val runId: String,
    val intent: String,
    val status: String,
    val totalMs: Long,
    val steps: List<AuditStep>
)

data class AuditStep(
    val action: String,
    val binder: String,
    val status: String,
    val ms: Long,
    val error: String? = null
)
