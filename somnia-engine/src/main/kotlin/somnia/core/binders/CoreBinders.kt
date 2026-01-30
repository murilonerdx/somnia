package somnia.core.binders

import somnia.core.*
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.kafka.core.KafkaTemplate
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.HttpMethod

@Component
class RepoCallBinder(private val spring: ApplicationContext) : StepBinder {
    override fun supports(step: StepIR): Boolean = step is StepIR.RepoCallStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val s = step as StepIR.RepoCallStep
        val repo = spring.getBean(s.beanName)
        val args = s.args.map { ctx.eval.eval(it, ctx) }.toTypedArray()

        val method = repo.javaClass.methods.find { it.name == s.method && it.parameterCount == args.size }
            ?: throw RuntimeException("Method ${s.method} not found in ${repo.javaClass.simpleName}")

        val out = method.invoke(repo, *args)

        // Handle Optional
        if (out is java.util.Optional<*>) {
            if (out.isEmpty) {
                if (s.orFailOk) return null
                if (s.orFailError != null) throw SomniaException("RuntimeError", s.orFailError, "Record not found")
                return null
            }
            return out.get()
        }

        if (out == null && s.orFailError != null) {
            throw SomniaException("RuntimeError", s.orFailError, "Resource not found")
        }

        return out
    }
}

@Component
class KafkaPublishBinder(
    private val template: KafkaTemplate<String, Any>,
    private val om: ObjectMapper
) : StepBinder {
    override fun supports(step: StepIR): Boolean = step is StepIR.KafkaPublishStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val s = step as StepIR.KafkaPublishStep
        val key = ctx.eval.eval(s.key, ctx)?.toString()
        val jsonObj = ctx.eval.eval(s.valueJson, ctx)
        
        template.send(s.topic, key, jsonObj)
        return null
    }
}

@Component
class HttpRequestBinder(private val spring: ApplicationContext) : StepBinder {
    override fun supports(step: StepIR): Boolean = step is StepIR.HttpRequestStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val s = step as StepIR.HttpRequestStep
        val beanName = "somniaHttpClient.${s.clientName}"
        val client = spring.getBean(beanName, WebClient::class.java)

        val body = s.jsonBody?.let { ctx.eval.eval(it, ctx) }

        val response = client.method(HttpMethod.valueOf(s.method))
            .uri(s.path)
            .let { if (body != null) it.bodyValue(body) else it }
            .retrieve()
            .toEntity(JsonNode::class.java)
            .block()

        if (response != null && response.statusCode.isError) {
            if (s.orFailError != null) {
                throw SomniaException("ExternalError", s.orFailError, "HTTP request failed with status ${response.statusCode}")
            }
        }
        
        return response?.body
    }
}
