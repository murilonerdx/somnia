package somnia.spring

import org.springframework.data.jpa.domain.Specification
import somnia.core.*
import org.springframework.context.ApplicationContext
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Component

@Component
class JpaSpecBinder(private val appCtx: ApplicationContext) : StepBinder {

    override fun supports(step: StepIR): Boolean = step is StepIR.JpaSpecStep

    @Suppress("UNCHECKED_CAST")
    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val jpaStep = step as StepIR.JpaSpecStep
        val repoName = "${jpaStep.entity.lowercase()}Repository"
        val repo = appCtx.getBean(repoName) as? JpaSpecificationExecutor<Any> 
            ?: throw RuntimeException("Repository $repoName not found or does not support Specifications")

        val spec = Specification<Any> { root, query, cb ->
            val predicates = jpaStep.predicates.map { pIR ->
                val value = ctx.eval.eval(pIR.valueExpr, ctx)
                when (pIR.op) {
                    "eq" -> cb.equal(root.get<Any>(pIR.field), value)
                    "ilike" -> cb.like(cb.lower(root.get(pIR.field)), "%${value.toString().lowercase()}%")
                    else -> throw IllegalArgumentException("Unsupported predicate op: ${pIR.op}")
                }
            }
            cb.and(*predicates.toTypedArray())
        }

        return repo.findAll(spec)
    }
}
