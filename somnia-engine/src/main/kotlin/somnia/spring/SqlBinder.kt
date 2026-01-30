package somnia.spring

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import somnia.core.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class SqlBinder(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) : StepBinder {

    override fun supports(step: StepIR): Boolean = step is StepIR.SqlQueryStep

    override fun execute(step: StepIR, ctx: ActionContext): Any? {
        val sqlStep = step as StepIR.SqlQueryStep
        
        val params = sqlStep.paramsExpr?.let { 
            ctx.eval.eval(it, ctx) as? Map<String, Any?> 
        } ?: emptyMap()

        val result = jdbcTemplate.queryForList(sqlStep.query, params)
        return objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(result)
    }
}
