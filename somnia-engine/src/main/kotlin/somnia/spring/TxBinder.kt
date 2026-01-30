package somnia.spring

import org.springframework.transaction.support.TransactionTemplate
import somnia.core.*
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Lazy

@Component
class TxBinder(
    private val transactionTemplate: TransactionTemplate,
    @Lazy private val interpreter: ActionInterpreter
) : StepBinder {

    override fun supports(step: StepIR): Boolean = step is StepIR.TxStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val txStep = step as StepIR.TxStep

        return transactionTemplate.execute { status ->
            var lastResult: Any? = null
            for (innerStep in txStep.steps) {
                try {
                    lastResult = interpreter.executeStep("transaction", innerStep, ctx)
                } catch (e: Exception) {
                    status.setRollbackOnly()
                    throw e
                }
            }
            lastResult
        }
    }
}
