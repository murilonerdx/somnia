package somnia.spring

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import somnia.lang.*
import somnia.core.*
import java.io.File

@Configuration
@org.springframework.context.annotation.ComponentScan(basePackages = ["somnia.core", "somnia.spring"])
class SomniaEngineAutoConfiguration(
    private val mongoRegistry: DynamicMongoRegistry,
    private val jpaRegistry: DynamicJpaRegistry
) {

    @Bean
    fun somniaRegistryTrigger(program: SomniaProgram): String {
        // Dynamic Registration of Infrastructure
        mongoRegistry.registerRepositories(program.act.entities, program.act.repositories)
        jpaRegistry.registerRepositories(program.act.entities, program.act.repositories)
        return "triggered"
    }

    @Bean
    fun programIR(program: SomniaProgram): ProgramIR {
        return Compiler().compile(program)
    }


    @Bean
    fun somniaKafkaProducerFactory(): org.springframework.kafka.core.ProducerFactory<String, Any> {
        val props = mutableMapOf<String, Any>()
        props[org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = System.getProperty("kafka.brokers") ?: "localhost:9092"
        props[org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = org.apache.kafka.common.serialization.StringSerializer::class.java
        props[org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = org.springframework.kafka.support.serializer.JsonSerializer::class.java
        return org.springframework.kafka.core.DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun somniaKafkaTemplate(pf: org.springframework.kafka.core.ProducerFactory<String, Any>): org.springframework.kafka.core.KafkaTemplate<String, Any> {
        return org.springframework.kafka.core.KafkaTemplate(pf)
    }

    @Bean
    fun somniaKafkaListenerFactory(): org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, Any> {
        val props = mutableMapOf<String, Any>()
        props[org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = System.getProperty("kafka.brokers") ?: "localhost:9092"
        props[org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = org.apache.kafka.common.serialization.StringDeserializer::class.java
        props[org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = org.springframework.kafka.support.serializer.JsonDeserializer::class.java
        
        val cf = org.springframework.kafka.core.DefaultKafkaConsumerFactory<String, Any>(props)
        val factory = org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = cf
        return factory
    }
}
