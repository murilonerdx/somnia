package somnia.core

import somnia.lang.*
import somnia.core.*

class Compiler {
    fun compile(program: SomniaProgram): ProgramIR {
        val sourcePath = program.sourcePath
        val baseDir = if (sourcePath != null && sourcePath.startsWith("file:")) {
             val f = java.io.File(java.net.URI(sourcePath))
             f.parentFile
        } else {
             // Default to current dir or dummy for classpath loading
             java.io.File(".")
        }
        
        PatternEngine().expand(program, baseDir)
        val actions = mutableMapOf<String, ActionIR>()
        val perms = mutableMapOf<String, String>()
        val plans = mutableMapOf<String, List<String>>()
        val renders = mutableMapOf<String, RenderIR>()

        program.act.actions.forEach { act ->
            val ir = compileAction(act)
            actions[act.name] = ir
            perms[act.name] = act.permission ?: "public"
            // Default plan for each action: just the action itself
            plans[act.name] = listOf(act.name)
        }

        program.act.renders.forEach { render ->
            renders[render.intent] = RenderIR(render.intent, compileExpr(render.logic))
        }

        return ProgramIR(
            actions = actions,
            renders = renders,
            plansByIntent = plans,
            permissionByAction = perms,
            staticRoutes = program.act.http?.let { http ->
                val base = http.base.removeSuffix("/")
                http.routes.map { route ->
                    val path = if (route.path.startsWith("/")) route.path else "/${route.path}"
                    route.copy(path = base + path)
                }
            } ?: emptyList(),
            kafkaTriggers = emptyList(), // Expand later if needed
            redisConfigs = emptyMap() // Expand later if needed
        )
    }

    private fun compileAction(act: ActionDecl): ActionIR {
        val params = act.params.map { ParamIR(it.name, it.type) }
        val steps = act.steps.map { compileStep(it) }
        return ActionIR(
            name = act.name,
            params = params,
            permission = act.permission ?: "public",
            returnType = act.returnType ?: "Void",
            steps = steps
        )
    }

    private fun compileStep(step: ActionStep): StepIR {
        return when (step) {
            is ActionStep.BindRepo -> StepIR.RepoCallStep(
                step.repo,
                step.method,
                step.args.map { compileExpr(it) },
                step.failError,
                step.failOk
            )
            is ActionStep.BindMap -> StepIR.MapStep(
                step.type,
                step.fields.mapValues { compileExpr(it.value) }
            )
            is ActionStep.ThenFail -> StepIR.ThenFailStep(step.error)
            is ActionStep.BindKafka -> StepIR.KafkaPublishStep(
                step.topic,
                compileExpr(step.keyExpr),
                compileExpr(step.valueExpr)
            )
            is ActionStep.BindHttp -> StepIR.HttpRequestStep(
                step.method,
                step.client,
                step.path,
                step.jsonBody?.let { compileExpr(it) },
                step.failError
            )
            is ActionStep.BindSyscall -> StepIR.SysCallStep(
                step.name,
                step.args.map { compileExpr(it) }
            )
            is ActionStep.BindSql -> StepIR.SqlQueryStep(
                step.query,
                step.params?.let { compileExpr(it) },
                step.decodeAs
            )
            is ActionStep.BindJpaSpec -> StepIR.JpaSpecStep(
                step.entity,
                step.predicates.map { PredicateIR(it.field, it.op, compileExpr(it.value)) }
            )
            is ActionStep.BindTx -> StepIR.TxStep(
                step.propagation,
                step.steps.map { compileStep(it) }
            )
            is ActionStep.BindRedis -> StepIR.RedisDelStep( // Assumindo DELETE por enquanto no MVP como exemplo
                step.client,
                step.keyExpr?.let { compileExpr(it) } ?: ExprIR.StrLit("")
            )
            is ActionStep.BindFlow -> StepIR.FlowIfNotNullStep(
                compileExpr(step.expr),
                step.thenSteps.map { compileStep(it) }
            )
            is ActionStep.SetResult -> StepIR.SetResultStep(
                step.action,
                compileExpr(step.value)
            )
            is ActionStep.SkipUntil -> StepIR.SkipUntilStep(step.target)
            else -> throw RuntimeException("Unsupported step in compiler: ${step.javaClass.simpleName}")
        }
    }

    private fun compileExpr(expr: Expr): ExprIR {
        return when (expr) {
            is Expr.Literal -> {
                val v = expr.value
                if (v is Number) ExprIR.NumLit(v.toDouble())
                else ExprIR.StrLit(v.toString())
            }
            is Expr.Variable -> ExprIR.Ident(expr.name)
            is Expr.Path -> ExprIR.Path(expr.parts)
            is Expr.JsonObj -> ExprIR.JsonObj(expr.fields.mapValues { compileExpr(it.value) })
            is Expr.Binary -> {
                // For now, treat binary as a built-in call or path if it's dot
                if (expr.op == TokenType.DOT) {
                     // Flatten path: a.b.c
                     val parts = flattenPath(expr)
                     ExprIR.Path(parts)
                } else {
                     // Generic binary operator
                     ExprIR.BuiltinCall(expr.op.name, listOf(compileExpr(expr.left), compileExpr(expr.right)))
                }
            }
            else -> throw RuntimeException("Unsupported expression in compiler: ${expr.javaClass.simpleName}")
        }
    }

    private fun flattenPath(expr: Expr.Binary): List<String> {
        val parts = mutableListOf<String>()
        
        fun collect(e: Expr) {
            when (e) {
                is Expr.Binary -> {
                    if (e.op == TokenType.DOT) {
                        collect(e.left)
                        collect(e.right)
                    } else {
                        throw RuntimeException("Non-dot binary in path")
                    }
                }
                is Expr.Variable -> parts.add(e.name)
                else -> throw RuntimeException("Unexpected expr in path: ${e.javaClass.simpleName}")
            }
        }
        
        collect(expr)
        return parts
    }
}
