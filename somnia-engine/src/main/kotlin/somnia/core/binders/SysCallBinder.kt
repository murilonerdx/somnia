package somnia.core.binders

import somnia.core.*
import org.springframework.stereotype.Component

@Component
class SysCallBinder : StepBinder {
    override fun supports(step: StepIR): Boolean = step is StepIR.SysCallStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val s = step as StepIR.SysCallStep
        val args = s.args.map { ctx.eval.eval(it, ctx) }

        return when (s.name) {
            "print" -> {
                println("[SOMNIA OUT] " + args.joinToString(" "))
                "Printed"
            }
            "uuid" -> java.util.UUID.randomUUID().toString()
            else -> throw RuntimeException("Unknown syscall: ${s.name}")
        }
    }
}
