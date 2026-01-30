package somnia.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import somnia.spring.SomniaAutoConfiguration

@SpringBootApplication
@Import(SomniaAutoConfiguration::class)
class TodoApplication

fun main(args: Array<String>) {
    runApplication<TodoApplication>(*args)
}
