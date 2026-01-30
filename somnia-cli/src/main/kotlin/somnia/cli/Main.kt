package somnia.cli

import picocli.CommandLine
import picocli.CommandLine.*
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "somnia",
    mixinStandardHelpOptions = true,
    version = ["0.1"],
    description = ["Somnia Boot Platform CLI"],
    subcommands = [
        DoctorCommand::class,
        FmtCommand::class,
        LintCommand::class
    ]
)
@Command(name = "doctor", description = ["Check project health"])
class DoctorCommand : Callable<Int> {
    @Option(names = ["-p", "--path"], defaultValue = ".")
    var path: String = "."

    override fun call(): Int {
        SomniaDoctor().diagnose(path)
        return 0
    }
}

@Command(name = "lint", description = ["Lint .somni files"])
class LintCommand : Callable<Int> {
    @Option(names = ["-p", "--path"], defaultValue = ".")
    var path: String = "."

    override fun call(): Int {
        SomniaLint().check(path)
        return 0
    }
}

@Command(name = "fmt", description = ["Format .somni files"])
class FmtCommand : Callable<Int> {
    override fun call(): Int {
        println("Formatting... (not implemented)")
        return 0
    }
}

class MainCommand : Callable<Int> {
    override fun call(): Int {
        CommandLine.usage(this, System.out)
        return 0
    }
}

fun main(args: Array<String>) {
    val exitCode = CommandLine(MainCommand()).execute(*args)
    exitProcess(exitCode)
}
