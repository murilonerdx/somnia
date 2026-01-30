package somnia.spring

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import somnia.lang.SomniaProgram

@Configuration
@EnableWebSecurity
class SomniaSecurityConfig(private val program: SomniaProgram) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val security = program.act.security

        // If no security block is defined, we might default to open or closed.
        // For Vertical 1, let's allow all if not configured (dev mode).
        if (security == null) {
            http.csrf { it.disable() }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
            return http.build()
        }

        http.csrf { it.disable() } // Often disabled for APIs
        
        // 1. Configure Authorization Rules
        http.authorizeHttpRequests { auth ->
            security.rules.forEach { rule ->
                // rules: allow roles ["ADMIN", "USER"] on "/api/admin/**"
                if (rule.roles.contains("PUBLIC")) {
                     auth.requestMatchers(rule.pattern).permitAll()
                } else {
                     auth.requestMatchers(rule.pattern).hasAnyRole(*rule.roles.toTypedArray())
                }
            }
            // Auto-permit internal endpoints if needed
            auth.requestMatchers("/error").permitAll()
            
            // Allow everything else? Or deny?
            // "Vertical 1" usually implies restrictive by default if security is ON.
            auth.anyRequest().authenticated()
        }

        // 2. Configure JWT if present
        val jwtConfig = security.jwt
        if (jwtConfig != null) {
            http.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwkSetUri(jwtConfig.jwksUrl)
                }
            }
        } else {
            // Basic auth or none?
            // If rules exist but no JWT, maybe Basic Auth?
            // For now, if no JWT config, maybe assume headers handled upstream or testing.
            http.httpBasic { }
        }

        return http.build()
    }
}
