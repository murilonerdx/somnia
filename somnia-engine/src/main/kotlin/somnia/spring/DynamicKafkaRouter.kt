package somnia.spring

import somnia.core.*
import somnia.lang.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.messaging.handler.annotation.Header
import org.springframework.kafka.support.KafkaHeaders

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["somnia.kafka.enabled"], havingValue = "true")
class DynamicKafkaRouter(
    private val engine: SomniaEngine,
    private val program: ProgramIR
) {
    @KafkaListener(
        topicPattern = ".*", // Match all for dynamic routing
        groupId = "\${somnia.kafka.group-id:somnia-dynamic-router}",
        containerFactory = "somniaKafkaListenerFactory"
    )
    fun onMessage(
        value: JsonNode,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?
    ) {
        // Find triggers for this topic from IR
        val triggers = program.kafkaTriggers.filter { it.topic == topic }
        
        for (trigger in triggers) {
            try {
                engine.run(
                    intent = trigger.intent,
                    args = mapOf("key" to key, "value" to value),
                    body = value,
                    principal = Principals.system("kafka")
                )
            } catch (e: Exception) {
                println("[SOMNIA KAFKA] Error processing topic $topic for intent ${trigger.intent}: ${e.message}")
            }
        }
    }
}
