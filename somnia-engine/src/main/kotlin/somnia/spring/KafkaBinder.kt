package somnia.spring

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaBinder(private val kafkaTemplate: KafkaTemplate<String, Any>) {
    fun publish(topic: String, key: String?, value: Any) {
        kafkaTemplate.send(topic, key, value)
    }
}
