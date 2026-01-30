package somnia.spring

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.RouterFunctions
import org.springframework.web.servlet.function.ServerResponse
import somnia.core.*
import somnia.core.SomniaException
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.function.RequestPredicates

@Configuration
class SomniaRouterConfig(
    private val program: ProgramIR,
    private val engine: SomniaEngine
) {

    @Bean
    fun somniaRouter(): RouterFunction<ServerResponse> {
        val builder = RouterFunctions.route()
        
        program.staticRoutes.forEach { route ->
            val predicate = RequestPredicates.method(HttpMethod.valueOf(route.method.uppercase()))
                .and(RequestPredicates.path(route.path))

            builder.route(predicate) { request ->
                val pathVariables = request.pathVariables()
                val queryParams = request.params().toSingleValueMap()
                val body = try { 
                    val b = request.body(Any::class.java)
                    println("[SOMNIA] Resolved body: $b")
                    b
                } catch (e: Exception) { 
                    println("[SOMNIA] Body error: ${e.message}")
                    null 
                }
                
                val combinedArgs = mutableMapOf<String, Any?>()
                combinedArgs.putAll(pathVariables)
                combinedArgs.putAll(queryParams)
                
                val principal = request.attribute("somniaPrincipal").orElse(null) as? PrincipalView ?: object : PrincipalView {
                    override fun isSystem() = true
                    override fun roles() = emptySet<String>()
                    override fun subject() = "anonymous"
                }
                
                try {
                    val res: ExecResult = engine.run(route.intent, combinedArgs, body, principal)
                    ServerResponse.status(route.status ?: 200).body(res.response ?: mapOf<String, Any>("status" to "success"))
                } catch (e: Exception) {
                    println("[SOMNIA] Router Error: ${e.message}")
                    e.printStackTrace()
                    val status = if (e is SomniaException) resolveStatus((e as SomniaException).errorName) else HttpStatus.INTERNAL_SERVER_ERROR
                    val message = e.message ?: "Unknown error"
                    ServerResponse.status(status).body(mapOf<String, Any>("error" to message))
                }
            }
        }
        
        return builder.build()
    }

    private fun resolveStatus(errorName: String): HttpStatus = when (errorName) {
        "NotFound" -> HttpStatus.NOT_FOUND
        "Conflict" -> HttpStatus.CONFLICT
        "Forbidden" -> HttpStatus.FORBIDDEN
        "Unauthorized" -> HttpStatus.UNAUTHORIZED
        else -> HttpStatus.BAD_REQUEST
    }
}
