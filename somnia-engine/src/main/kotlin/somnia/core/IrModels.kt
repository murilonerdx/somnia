package somnia.core

import somnia.lang.*

// --- 1) IR v0.2 (Optimized for execution) ---

data class ProgramIR(
    val actions: Map<String, ActionIR>,
    val renders: Map<String, RenderIR>,
    val plansByIntent: Map<String, List<String>>,
    val permissionByAction: Map<String, String>,
    val staticRoutes: List<RouteDecl> = emptyList(),
    val kafkaTriggers: List<KafkaTriggerDecl> = emptyList(),
    val redisConfigs: Map<String, RedisConfig> = emptyMap()
)

data class RedisConfig(val name: String, val url: String, val prefix: String)

data class ActionIR(
    val name: String,
    val params: List<ParamIR>,
    val permission: String,
    val returnType: String,
    val steps: List<StepIR>
)

data class ParamIR(val name: String, val type: String)

sealed interface StepIR {
    data class RepoCallStep(
        val beanName: String,
        val method: String,
        val args: List<ExprIR>,
        val orFailError: String? = null,
        val orFailOk: Boolean = false
    ) : StepIR

    data class KafkaPublishStep(
        val topic: String,
        val key: ExprIR,
        val valueJson: ExprIR
    ) : StepIR

    data class HttpRequestStep(
        val method: String,
        val clientName: String,
        val path: String,
        val jsonBody: ExprIR? = null,
        val orFailError: String? = null
    ) : StepIR

    data class MapStep(
        val dtoType: String,
        val fields: Map<String, ExprIR>
    ) : StepIR

    data class ThenFailStep(val errorName: String) : StepIR
    
    data class SysCallStep(
        val name: String,
        val args: List<ExprIR>
    ) : StepIR

    // --- Advanced v0.2 Steps ---
    data class RedisDelStep(val clientName: String, val keyExpr: ExprIR) : StepIR
    
    data class FlowIfNotNullStep(val expr: ExprIR, val thenSteps: List<StepIR>) : StepIR
    
    data class SetResultStep(val actionName: String, val valueExpr: ExprIR) : StepIR
    
    data class SkipUntilStep(val actionName: String) : StepIR
    
    data class AuditFetchStep(val runIdExpr: ExprIR, val orFailError: String?) : StepIR

    data class SqlQueryStep(val query: String, val paramsExpr: ExprIR?, val decodeAs: String? = null) : StepIR
    data class JpaSpecStep(val entity: String, val predicates: List<PredicateIR>) : StepIR
    data class TxStep(val propagation: String, val steps: List<StepIR>) : StepIR
}

data class PredicateIR(val field: String, val op: String, val valueExpr: ExprIR)

sealed interface ExprIR {
    data class StrLit(val value: String) : ExprIR
    data class NumLit(val value: Double) : ExprIR
    data class Ident(val name: String) : ExprIR
    data class Path(val parts: List<String>) : ExprIR
    data class BuiltinCall(val name: String, val args: List<ExprIR>) : ExprIR
    data class JsonObj(val fields: Map<String, ExprIR>) : ExprIR
    data class ResultRef(val actionName: String) : ExprIR
}

data class RenderIR(val intent: String, val expr: ExprIR)
