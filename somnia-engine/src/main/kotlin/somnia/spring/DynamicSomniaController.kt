package somnia.spring

import org.springframework.web.bind.annotation.*
import somnia.core.*
import somnia.lang.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication

@RestController
class DynamicSomniaController(
    private val program: ProgramIR,
    private val engine: SomniaEngine
) {

    @RequestMapping(value = ["/somnia/api/**"])
    fun handleRequest(
        request: HttpServletRequest, 
        @RequestBody(required = false) body: Any?,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val requestPath = request.requestURI.removePrefix("/somnia/api")
        val method = request.method

        // Find matching route from IR
        var exposure: RouteDecl? = null
        var pathVars = mapOf<String, String>()
        
        for (route in program.staticRoutes) {
            if (route.method.equals(method, ignoreCase = true)) {
                val match = matchPath(route.path, requestPath)
                if (match != null) {
                    exposure = route
                    pathVars = match
                    break
                }
            }
        }

        if (exposure == null) {
            return ResponseEntity.status(404).body("No SOMNIA exposure found for $method $requestPath")
        }

        // 1. Prepare Principal
        val principal = if (authentication != null) {
            Principals.fromAuthentication(authentication)
        } else {
            Principals.system("anonymous")
        }

        // 2. Prepare combined arguments
        val queryParams = request.parameterMap.mapValues { it.value.first() }
        val combinedArgs = mutableMapOf<String, Any?>()
        combinedArgs.putAll(queryParams)
        combinedArgs.putAll(pathVars)
        
        // 3. Execution via Engine (v0.2)
        return try {
            val execResult = engine.run(exposure.intent, combinedArgs, body, principal)
            
            val status = exposure.status ?: 200
            val responseBody = execResult.response ?: mapOf("status" to "success")
            
            ResponseEntity.status(status).body(responseBody)
        } catch (e: SomniaException) {
            val status = when (e.errorName) {
                "NotFound" -> 404
                "Conflict" -> 409
                "Forbidden" -> 403
                "Unauthorized" -> 401
                else -> 400
            }
            ResponseEntity.status(status).body(mapOf("code" to e.errorCode, "message" to e.message))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to e.message))
        }
    }
    
    private fun matchPath(routePath: String, requestPath: String): Map<String, String>? {
        val routeParts = routePath.split("/").filter { it.isNotEmpty() }
        val reqParts = requestPath.split("/").filter { it.isNotEmpty() }
        
        if (routeParts.size != reqParts.size) return null
        
        val vars = mutableMapOf<String, String>()
        
        for (i in routeParts.indices) {
            val rPart = routeParts[i]
            val reqPart = reqParts[i]
            
            if (rPart.startsWith("{") && rPart.endsWith("}")) {
                val varName = rPart.substring(1, rPart.length - 1)
                vars[varName] = reqPart
            } else if (rPart != reqPart) {
                return null
            }
        }
        return vars
    }
}
