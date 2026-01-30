package somnia.core.binders

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import somnia.core.*
import java.time.Duration

@Component
class RedisBinder(
    private val spring: ApplicationContext,
    private val om: ObjectMapper
) : StepBinder {
    override fun supports(step: StepIR): Boolean =
        step is StepIR.RedisDelStep || 
        step.javaClass.simpleName.contains("Redis") ||
        (step is StepIR.RepoCallStep && step.beanName.startsWith("redis:"))

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        return when (step) {
            is StepIR.RedisDelStep -> del(step, ctx)
            is StepIR.RepoCallStep -> if (step.method == "get") getLegacy(step, ctx) else throw RuntimeException("Unsupported redis repo method")
            else -> {
                if (step.javaClass.simpleName == "RedisGetStep") return getReflect(step, ctx)
                if (step.javaClass.simpleName == "RedisSetStep") return setReflect(step, ctx)
                throw RuntimeException("Unsupported redis step: ${step.javaClass.simpleName}")
            }
        }
    }

    private fun getTemplate(clientName: String): StringRedisTemplate {
        // Handle namespaced bean names
        val beanName = if (clientName.isEmpty()) "stringRedisTemplate" else "somniaRedis.$clientName.template"
        return spring.getBean(beanName, StringRedisTemplate::class.java)
    }

    private fun getPrefix(clientName: String): String {
        return try {
            spring.getBean("somniaRedis.$clientName.prefix", String::class.java)
        } catch (e: Exception) {
            ""
        }
    }

    private fun del(step: StepIR.RedisDelStep, ctx: ActionContext): Any? {
        val rawKey = ctx.eval.eval(step.keyExpr, ctx).toString()
        val key = getPrefix(step.clientName) + rawKey
        getTemplate(step.clientName).delete(key)
        return null
    }

    private fun getLegacy(step: StepIR.RepoCallStep, ctx: ActionContext): Any? {
        val clientName = step.beanName.removePrefix("redis:")
        val rawKey = ctx.eval.eval(step.args[0], ctx).toString()
        val key = getPrefix(clientName) + rawKey
        val raw = getTemplate(clientName).opsForValue().get(key) ?: return null
        return om.readTree(raw)
    }

    private fun getReflect(step: Any, ctx: ActionContext): Any? {
        val clientName = step.reflectionGet("clientName") as String
        val keyExpr = step.reflectionGet("keyExpr") as ExprIR
        val rawKey = ctx.eval.eval(keyExpr, ctx).toString()
        val key = getPrefix(clientName) + rawKey
        val raw = getTemplate(clientName).opsForValue().get(key) ?: return null
        return om.readTree(raw)
    }

    private fun setReflect(step: Any, ctx: ActionContext): Any? {
        val clientName = step.reflectionGet("clientName") as String
        val keyExpr = step.reflectionGet("keyExpr") as ExprIR
        val valueExpr = step.reflectionGet("valueExpr") as ExprIR
        val ttl = (step.reflectionGet("ttlSeconds") as? Number)?.toLong() ?: 3600L

        val rawKey = ctx.eval.eval(keyExpr, ctx).toString()
        val key = getPrefix(clientName) + rawKey
        val value = ctx.eval.eval(valueExpr, ctx)
        val rawValue = om.writeValueAsString(value)
        
        getTemplate(clientName).opsForValue().set(key, rawValue, Duration.ofSeconds(ttl))
        return null
    }

    private fun Any.reflectionGet(name: String): Any? {
        val field = this.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
    }
}
