package somnia.spring

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import somnia.core.PrincipalView

@Component
class PermissionGate {
    // V1: Simple map-based or logic-based check
    private val PERM_ROLES = mapOf(
        "db.read" to setOf("USER", "ADMIN"),
        "db.write" to setOf("ADMIN"),
        "network" to setOf("ADMIN"),
        "stdout" to setOf("USER", "ADMIN")
    )

    fun check(permission: String, principal: PrincipalView) {
        if (principal.isSystem()) return
        
        val allowed = PERM_ROLES[permission] ?: return // If unknown, allow? Or deny? Spec says Forbidden
        
        val roles = principal.roles()
        if (roles.intersect(allowed).isEmpty()) {
            throw somnia.core.SomniaException("Forbidden", "PERMISSION_DENIED", "Required one of: $allowed")
        }
    }
}

object Principals {
    fun system(subject: String) = object : PrincipalView {
        override fun isSystem() = true
        override fun roles() = setOf("SYSTEM")
        override fun subject() = subject
    }

    fun fromAuthentication(auth: Authentication): PrincipalView {
        val roles = auth.authorities
            .map { it.authority }
            .filter { it.startsWith("ROLE_") }
            .map { it.substring(5) }
            .toSet()
        
        return object : PrincipalView {
            override fun isSystem() = false
            override fun roles() = roles
            override fun subject() = auth.name
        }
    }
}
