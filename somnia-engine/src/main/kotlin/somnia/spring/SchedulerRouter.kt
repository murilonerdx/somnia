package somnia.spring

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import somnia.core.*
import somnia.lang.*

@Component
class SchedulerRouter(
    private val program: ProgramIR,
    private val engine: SomniaEngine
) {

    // No MVP, vamos simular o agendamento fixo ou ler do ProgramIR
    // Em uma versão real, usaríamos TaskScheduler para registrar dinamicamente
    
    @Scheduled(cron = "\${somnia.jobs.cleanup.cron:0 */10 * * * *}")
    fun runCleanup() {
        engine.run("system.cleanup", emptyMap(), null, Principals.system("scheduler"))
    }
}
