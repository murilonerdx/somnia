package somnia.core

import somnia.lang.*
import java.io.File

class PatternEngine {
    private val loader = ModuleLoader()

    fun expand(program: SomniaProgram, baseDir: File) {
        // 1. Load all modules (recursive imports)
        loader.loadImports(program, baseDir)
        
        // 2. Expand Patterns
        // We iterate copy of usages to avoid concurrent mod exceptions if expansion adds usages (recursive macros? Maybe later)
        val usages = program.act.patternUsages.toList()
        
        usages.forEach { usage ->
            expandUsage(program, usage)
        }
    }

    private fun expandUsage(program: SomniaProgram, usage: PatternUsage) {
        val def = program.act.patternDefs.find { it.name == usage.name }
        if (def == null) {
            println("[SOMNIA] Warning: Pattern definition '${usage.name}' not found.")
            return
        }

        if (def.params.size != usage.args.size) {
             println("[SOMNIA] Warning: Pattern '${usage.name}' expects ${def.params.size} args but got ${usage.args.size}.")
             return
        }

        // Build Substitution Map
        val subMap = mutableMapOf<String, String>()
        for (i in def.params.indices) {
            val param = def.params[i]
            val arg = usage.args[i]
            val valStr = exprToString(arg)
            subMap[param] = valStr
        }

        val body = def.body
        
        // Clone and Substitute
        // Entities
        body.entities.forEach { e ->
            program.act.entities.add(e.copy(
                name = sub(e.name, subMap),
                table = sub(e.table, subMap),
                fields = e.fields.map { subField(it, subMap) }
            ))
        }

        // Repos
        body.repositories.forEach { r ->
            program.act.repositories.add(r.copy(
                name = sub(r.name, subMap),
                entityType = sub(r.entityType, subMap),
                methods = r.methods.map { subRepoMethod(it, subMap) }
            ))
        }

        // Actions
        body.actions.forEach { a ->
            program.act.actions.add(a.copy(
                name = sub(a.name, subMap),
                params = a.params.map { ParamDecl(sub(it.name, subMap), sub(it.type, subMap)) },
                returnType = a.returnType?.let { sub(it, subMap) },
                steps = a.steps.map { subStep(it, subMap) }
            ))
        }

        // DTOs
        body.dtos.forEach { d ->
            program.act.dtos.add(d.copy(
                name = sub(d.name, subMap),
                fields = d.fields.map { subField(it, subMap) }
            ))
        }
        
        // HTTP Routes (Merge if needed, or add to main http block)
        if (body.http != null) {
            if (program.act.http == null) {
                program.act.http = HttpDecl("/", listOf())
            }
            val newRoutes = body.http!!.routes.map { route ->
                route.copy(
                    path = sub(route.path, subMap),
                    intent = sub(route.intent, subMap),
                    body = route.body?.let { subField(it, subMap) }
                )
            }
            val current = program.act.http!!.routes.toMutableList()
            current.addAll(newRoutes)
            program.act.http = program.act.http!!.copy(routes = current)
        }
        
        // Config, Renders... simple logic for MVP
        body.renders.forEach { r ->
            program.act.renders.add(r.copy(
                 intent = sub(r.intent, subMap),
                 logic = subExpr(r.logic, subMap)
            ))
        }
        
        println("[SOMNIA] Expanded pattern '${usage.name}' with args $subMap")
    }

    private fun sub(text: String, map: Map<String, String>): String {
        var result = text
        map.forEach { (k, v) ->
            // Exact match
            if (result == k) result = v
            // Template match
            result = result.replace("\${$k}", v)
        }
        return result
    }

    private fun exprToString(expr: Expr): String {
        return when (expr) {
            is Expr.Variable -> expr.name
            is Expr.Literal -> expr.value.toString()
            else -> "unknown"
        }
    }
    
    // --- Deep Substitution Helpers ---

    private fun subField(f: FieldDecl, map: Map<String, String>) = f.copy(
        name = sub(f.name, map),
        type = sub(f.type, map)
    )

    private fun subRepoMethod(m: RepoMethod, map: Map<String, String>) = m.copy(
        name = sub(m.name, map),
        args = m.args.map { subField(it, map) },
        returnType = sub(m.returnType, map)
    )

    private fun subStep(s: ActionStep, map: Map<String, String>): ActionStep {
        return when (s) {
            is ActionStep.BindRepo -> s.copy(
                repo = sub(s.repo, map),
                method = sub(s.method, map),
                args = s.args.map { subExpr(it, map) }
            )
            is ActionStep.BindSql -> s.copy(
                query = sub(s.query, map),
                params = s.params?.let { subExpr(it, map) }
            )
            is ActionStep.BindTx -> s.copy(
                steps = s.steps.map { subStep(it, map) }
            )
            is ActionStep.SetResult -> s.copy(
                action = sub(s.action, map),
                value = subExpr(s.value, map)
            )
            // ... implement others as needed for Crud ...
            else -> s 
        }
    }

    private fun subExpr(e: Expr, map: Map<String, String>): Expr {
        return when (e) {
            is Expr.Variable -> {
                val newName = sub(e.name, map)
                Expr.Variable(newName)
            }
            is Expr.Literal -> {
                if (e.value is String) Expr.Literal(sub(e.value as String, map)) else e
            }
            is Expr.JsonObj -> Expr.JsonObj(e.fields.mapValues { subExpr(it.value, map) })
            // Recurse for Binary, Call, etc if needed
            else -> e
        }
    }
}
