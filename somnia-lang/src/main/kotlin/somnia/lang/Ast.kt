package somnia.lang

data class ImportDecl(val path: String)

data class SomniaProgram(
    val imports: MutableList<ImportDecl> = mutableListOf(),
    val id: IdBlock = IdBlock(),
    val ego: EgoBlock = EgoBlock(),
    val act: ActBlock = ActBlock(),
    var sourcePath: String? = null
)

// --- EXPRESSIONS ---
sealed class Expr {
    data class Literal(val value: Any?) : Expr() // Strings, Numbers, Booleans
    data class Variable(val name: String) : Expr()
    data class Binary(val left: Expr, val op: TokenType, val right: Expr) : Expr()
    data class Call(val callee: Expr, val args: List<Expr>) : Expr()
    data class Grouping(val expression: Expr) : Expr()
    data class Unary(val op: TokenType, val right: Expr) : Expr()
    data class Path(val parts: List<String>) : Expr()
    data class JsonObj(val fields: Map<String, Expr>) : Expr()
}

// --- ID Layer ---
data class IdBlock(
    val declarations: MutableList<Declaration> = mutableListOf(),
    val associations: MutableList<Association> = mutableListOf(),
    val rules: MutableList<Rule> = mutableListOf(),
    val consts: MutableList<ConstDecl> = mutableListOf(),
    val vars: MutableList<VarDecl> = mutableListOf()
)

data class Declaration(val kind: String, val name: String, val weight: Double, val params: List<String> = listOf())
data class Association(val from: String, val to: String, val weight: Double)

data class Rule(
    val condition: Condition,
    val proposal: ProposalTemplate,
    val weight: Double
)

sealed class Condition
data class Predicate(val name: String, val args: List<String>) : Condition()
data class AndCondition(val left: Condition, val right: Condition) : Condition()

data class ProposalTemplate(val name: String, val args: List<Expr>, val probability: Double = 1.0)

data class ConstDecl(val name: String, val value: Expr)
data class VarDecl(val name: String, val value: Expr?)

// --- EGO Layer ---
data class EgoBlock(
    val commands: MutableList<EgoCommand> = mutableListOf(),
    val functions: MutableList<FunctionDecl> = mutableListOf()
)

data class FunctionDecl(val name: String, val params: List<ParamDecl>, val body: List<Statement>)

sealed class Statement {
    data class Return(val value: Expr) : Statement()
    data class If(val condition: Expr, val thenBranch: List<Statement>, val elseBranch: List<Statement>?) : Statement()
    data class Var(val name: String, val initializer: Expr?) : Statement()
    data class Assign(val name: String, val value: Expr) : Statement()
    data class Expression(val expr: Expr) : Statement()
}

sealed class EgoCommand {
    data class AttentionCommand(val budget: Double) : EgoCommand()
    data class ForbidCommand(val target: String, val condition: String? = null) : EgoCommand() 
    data class PreferCommand(val target: String, val condition: String, val weight: Double) : EgoCommand()
    data class SelectCommand(val strategy: String, val param: Int) : EgoCommand() 
    data class DreamCommand(val cycles: Int) : EgoCommand()
    data class WhereCommand(val condition: Expr) : EgoCommand()
}

// --- ACT Layer ---
data class ActBlock(
    var app: AppDecl? = null,
    var config: ConfigDecl? = null,
    val dtos: MutableList<DtoDecl> = mutableListOf(),
    val entities: MutableList<EntityDecl> = mutableListOf(),
    val repositories: MutableList<RepoDecl> = mutableListOf(),
    val errors: MutableList<ErrorMapping> = mutableListOf(),
    var security: SecurityDecl? = null,
    var http: HttpDecl? = null,
    
    val intents: MutableList<String> = mutableListOf(),
    val actions: MutableList<ActionDecl> = mutableListOf(),
    val renders: MutableList<RenderDecl> = mutableListOf(),
    val contracts: MutableList<ContractDecl> = mutableListOf(),
    val patternDefs: MutableList<PatternDef> = mutableListOf(),
    val patternUsages: MutableList<PatternUsage> = mutableListOf(),
    
    val kafkaDecls: MutableList<KafkaDecl> = mutableListOf(),
    val kafkaTriggers: MutableList<KafkaTriggerDecl> = mutableListOf(),
    val httpClients: MutableList<HttpClientDecl> = mutableListOf(),
    var redis: RedisDecl? = null
)

data class AppDecl(val name: String, val packageName: String, val bootVersion: String)
data class ConfigDecl(val prefix: String, val fields: List<FieldDecl>)
data class DtoDecl(val name: String, val fields: List<FieldDecl>)
data class EntityDecl(val name: String, val table: String, val fields: List<FieldDecl>)
data class RepoDecl(val name: String, val entityType: String, val idType: String, val methods: List<RepoMethod>)
data class RepoMethod(val name: String, val args: List<FieldDecl>, val returnType: String)
data class FieldDecl(val name: String, val type: String, val annotations: List<String> = listOf())
data class ErrorMapping(val source: String, val status: Int, val code: String)

data class SecurityDecl(
    val jwt: JwtConfig?,
    val rules: List<SecurityRule> = listOf(),
    val permissions: List<PermissionDecl> = listOf()
)
data class JwtConfig(val issuer: String, val jwksUrl: String)
data class SecurityRule(val roles: List<String>, val method: String?, val pattern: String)
data class PermissionDecl(val name: String, val roles: List<String>)

data class HttpDecl(val base: String, val routes: List<RouteDecl>)
data class RouteDecl(
    val method: String, 
    val path: String, 
    val intent: String,
    val args: List<FieldDecl> = listOf(),
    val body: FieldDecl? = null,
    val returnType: String? = null,
    val status: Int? = null,
    val roles: List<String> = listOf()
)

data class ParamDecl(val name: String, val type: String)

data class ActionDecl(
    val name: String, 
    val params: List<ParamDecl>,
    val permission: String?, 
    val returnType: String?,
    val steps: List<ActionStep> = listOf()
)

sealed class ActionStep {
    data class BindRepo(val repo: String, val method: String, val args: List<Expr>, val failError: String? = null, val failOk: Boolean = false) : ActionStep()
    data class BindKafka(val topic: String, val keyExpr: Expr, val valueExpr: Expr) : ActionStep()
    data class BindHttp(val method: String, val client: String, val path: String, val jsonBody: Expr?, val failError: String? = null) : ActionStep()
    data class BindMap(val type: String, val fields: Map<String, Expr>) : ActionStep()
    data class ThenFail(val error: String) : ActionStep()
    data class BindSyscall(val name: String, val args: List<Expr>) : ActionStep()
    data class BindRedis(val client: String, val method: String, val keyExpr: Expr?, val valueExpr: Expr? = null, val ttl: Int? = null) : ActionStep()
    data class BindSql(val query: String, val params: Expr?, val decodeAs: String? = null) : ActionStep()
    data class BindJpaSpec(val entity: String, val predicates: List<PredicateExpr>) : ActionStep()
    data class BindTx(val propagation: String, val steps: List<ActionStep>) : ActionStep()
    data class BindFlow(val expr: Expr, val thenSteps: List<ActionStep>) : ActionStep()
    data class SetResult(val action: String, val value: Expr) : ActionStep()
    data class SkipUntil(val target: String) : ActionStep()
}

sealed class ActionBinding {
    data class RepoCall(val repo: String, val method: String, val args: List<Expr>, val failError: String? = null, val failOk: Boolean = false) : ActionBinding()
    data class MapCreation(val type: String, val fields: Map<String, Expr>) : ActionBinding()
}

data class RenderDecl(val intent: String, val logic: Expr)
data class ContractDecl(val name: String, val generics: List<String>, val methods: List<RepoMethod>)
data class PatternDef(val name: String, val params: List<String>, val body: ActBlock, val implements: List<String> = listOf())
data class PatternUsage(val name: String, val args: List<Expr>)

data class KafkaDecl(val brokersRef: String, val topics: List<TopicDecl>)
data class TopicDecl(val name: String, val keyType: String, val valueType: String, val consumerGroup: String?, val deadLetter: String?)
data class KafkaTriggerDecl(val topic: String, val intent: String, val args: List<FieldDecl>)

data class HttpClientDecl(
    val name: String,
    val baseUrlRef: String,
    val authBearerRef: String?,
    val timeoutMs: Int?,
    val retryTimes: Int?,
    val backoffMs: Int?
)

data class RedisDecl(val name: String, val url: String, val prefix: String)
