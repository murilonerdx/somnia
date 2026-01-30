package somnia.spring

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import somnia.core.ProgramIR
import java.time.Duration

@Component
class SomniaRateLimitFilter(
    private val program: ProgramIR,
    private val spring: org.springframework.context.ApplicationContext
) : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val res = response as HttpServletResponse

        val requestPath = req.requestURI
        val method = req.method

        val principal = req.getHeader("Authorization") ?: "anonymous"
        val limitKey = "ratelimit:$method:$requestPath:$principal"
        
        try {
            // Find redis template. Using "stringRedisTemplate" as default if "main" doesn't exist.
            val template = try {
                spring.getBean("somniaRedis.main.template", StringRedisTemplate::class.java)
            } catch (e: Exception) {
                spring.getBean(StringRedisTemplate::class.java)
            }
            
            val current = template.opsForValue().increment(limitKey) ?: 0L
            
            if (current == 1L) {
                template.expire(limitKey, Duration.ofMinutes(1))
            }
            
            if (current > 100) { 
                res.status = 429
                res.contentType = "application/json"
                res.writer.write("{\"code\":\"RATE_LIMIT\",\"message\":\"Too many requests\"}")
                return
            }
        } catch (e: Exception) {
            // Redis error, allow request in v0.2
        }

        chain.doFilter(request, response)
    }
}
