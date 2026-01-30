package somnia.spring

import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.springframework.stereotype.Component
import somnia.lang.EntityDecl
import somnia.lang.RepoDecl

@Component
class DynamicMongoRegistry : ApplicationContextAware {
    private lateinit var applicationContext: ApplicationContext

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    fun registerRepositories(entities: List<EntityDecl>, repos: List<RepoDecl>) {
        val mongoTemplate = try {
            applicationContext.getBean(MongoTemplate::class.java)
        } catch (e: Exception) {
            println("[SOMNIA] No MongoTemplate found, using In-Memory Storage.")
            null
        }
        
        val registry = (applicationContext as? org.springframework.context.ConfigurableApplicationContext)?.beanFactory as? BeanDefinitionRegistry
            ?: return

        repos.forEach { repo ->
            val entity = entities.find { it.name == repo.entityType } ?: return@forEach
            // Derive collection name from entity table or name
            val collectionName = entity.table.ifEmpty { entity.name.lowercase() }

            println("[SOMNIA] Registering Dynamic MongoDB Repository: ${repo.name} -> Collection: $collectionName")

            val beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(GenericSomniaRepository::class.java)
                .addConstructorArgValue(collectionName)
                .addConstructorArgValue(mongoTemplate)
                .beanDefinition

            // Register bean with lowercased name, e.g. "taskRepo"
            registry.registerBeanDefinition(repo.name.replaceFirstChar { it.lowercase() }, beanDefinition)
        }
    }
}
