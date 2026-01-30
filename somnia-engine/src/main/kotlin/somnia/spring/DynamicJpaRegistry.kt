package somnia.spring

import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import somnia.lang.EntityDecl
import somnia.lang.RepoDecl

@Component
class DynamicJpaRegistry : ApplicationContextAware {
    private lateinit var applicationContext: ApplicationContext

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    fun registerRepositories(entities: List<EntityDecl>, repos: List<RepoDecl>) {
        println("[SOMNIA] JPA Registry processing ${repos.size} repos")
        val registry = (applicationContext as? org.springframework.context.ConfigurableApplicationContext)?.beanFactory as? BeanDefinitionRegistry
            ?: return

        val jdbc = applicationContext.getBean(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate::class.java)

        repos.forEach { repo ->
            try {
                val entity = entities.find { it.name == repo.entityType || it.name == repo.entityType.split(".").last() }
                    ?: throw IllegalStateException("Entity description not found for ${repo.entityType}")

                println("[SOMNIA] Registering Dynamic Repository: ${repo.name} for table ${entity.table}")

                val beanDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(SomniaDynamicJpaRepository::class.java)
                    .addConstructorArgValue(jdbc)
                    .addConstructorArgValue(entity)
                    .beanDefinition

                registry.registerBeanDefinition(repo.name.replaceFirstChar { it.lowercase() }, beanDefinition)
                
            } catch (e: Exception) {
                println("[SOMNIA] Skipping Repository ${repo.name}: ${e.message}")
            }
        }
    }
}
