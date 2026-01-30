package somnia.core

import org.springframework.stereotype.Component

interface ActionRegistry {
    fun get(name: String): ActionIR
    fun exists(name: String): Boolean
}

@Component
class DefaultActionRegistry(private val program: ProgramIR) : ActionRegistry {
    override fun get(name: String): ActionIR {
        return program.actions[name] ?: throw RuntimeException("Unknown action: $name")
    }

    override fun exists(name: String): Boolean = program.actions.containsKey(name)
}
