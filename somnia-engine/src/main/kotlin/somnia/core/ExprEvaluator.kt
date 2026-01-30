package somnia.core

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class ExprEvaluator(
    private val om: ObjectMapper,
    private val spring: ApplicationContext
) {
    fun eval(expr: ExprIR, ctx: ActionContext): Any? {
        return when (expr) {
            is ExprIR.StrLit -> expr.value
            is ExprIR.NumLit -> expr.value
            is ExprIR.Ident -> resolveIdent(expr.name, ctx)
            is ExprIR.ResultRef -> ctx.result(expr.actionName)
            is ExprIR.Path -> resolvePath(expr.parts, ctx)
            is ExprIR.BuiltinCall -> evalBuiltin(expr, ctx)
            is ExprIR.JsonObj -> {
                val map = mutableMapOf<String, Any?>()
                expr.fields.forEach { entry ->
                    map[entry.key] = eval(entry.value, ctx)
                }
                map
            }
            else -> throw RuntimeException("Unknown expr: ${expr.javaClass.simpleName}")
        }
    }

    private fun resolveIdent(name: String, ctx: ActionContext): Any? {
        if ("body" == name || "req" == name) return ctx.body
        if (ctx.args.containsKey(name)) return ctx.args[name]
        val r = ctx.results[name]
        if (r != null) return r
        throw RuntimeException("Unbound identifier: $name")
    }

    private fun resolvePath(parts: List<String>, ctx: ActionContext): Any? {
        var cur = resolveIdent(parts[0], ctx)
        for (i in 1 until parts.size) {
            cur = reflectGet(cur!!, parts[i])
        }
        return cur
    }

    private fun evalBuiltin(c: ExprIR.BuiltinCall, ctx: ActionContext): Any? {
        return when (c.name) {
            "uuid" -> java.util.UUID.randomUUID()
            "str" -> eval(c.args[0], ctx).toString()
            "concat" -> c.args.map { eval(it, ctx).toString() }.joinToString("")
            else -> throw RuntimeException("Unknown builtin: ${c.name}")
        }
    }

    private fun buildDto(dtoType: String, fields: Map<String, ExprIR>, ctx: ActionContext): Any? {
        try {
            // v0.2: search for class in common generic packages
            val clazz = Class.forName("com.example.demo.dto.$dtoType") 
            val obj = clazz.getDeclaredConstructor().newInstance()
            for ((key, valueExpr) in fields) {
                val v = eval(valueExpr, ctx)
                reflectSet(obj, key, v)
            }
            return obj
        } catch (ex: Exception) {
            throw RuntimeException("DTO build failed: $dtoType", ex)
        }
    }

    private fun reflectGet(obj: Any, field: String): Any? {
        if (obj is Map<*, *>) {
            return obj[field]
        }
        return try {
            val m = obj.javaClass.getMethod("get" + cap(field))
            m.invoke(obj)
        } catch (e: Exception) {
            // try as field if property fails
            try {
                val f = obj.javaClass.getDeclaredField(field)
                f.isAccessible = true
                f.get(obj)
            } catch (e2: Exception) {
                throw RuntimeException("No getter for $field on ${obj.javaClass.simpleName}")
            }
        }
    }

    private fun reflectSet(obj: Any, field: String, value: Any?) {
        if (value == null) return
        try {
            val m = obj.javaClass.methods.find { 
                it.name == "set" + cap(field) && it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(value.javaClass)
            }
            m?.invoke(obj, value) ?: throw RuntimeException("No setter for $field")
        } catch (e: Exception) {
            throw RuntimeException("Setter failed: $field", e)
        }
    }

    private fun cap(s: String): String = s.substring(0, 1).uppercase() + s.substring(1)
}


data class ActionResult(val success: Boolean, val output: Any?)
