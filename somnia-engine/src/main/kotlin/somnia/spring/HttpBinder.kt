package somnia.spring

import somnia.core.*
import org.springframework.stereotype.Component

@Component
class HttpBinder : StepBinder {
    override fun supports(step: StepIR): Boolean = step is StepIR.HttpRequestStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val httpStep = step as StepIR.HttpRequestStep
        // Implementação real usaria WebClient ou similar
        println("Making HTTP ${httpStep.method} to ${httpStep.path}")
        return mapOf("status" to "ok")
    }
}
