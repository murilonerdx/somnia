package somnia.spring

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import somnia.lang.EntityDecl
import java.util.UUID

class SomniaDynamicJpaRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val entity: EntityDecl
) {
    private val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())

    init {
        createTableIfNotExists()
    }

    private fun createTableIfNotExists() {
        val fieldsSql = entity.fields.joinToString(", ") { field ->
            val sqlType = when (field.type.uppercase()) {
                "UUID" -> "UUID"
                "STRING" -> "VARCHAR(255)"
                "DOUBLE" -> "DOUBLE"
                "INT", "INTEGER" -> "INT"
                "BOOLEAN" -> "BOOLEAN"
                else -> "VARCHAR(255)"
            }
            val pk = if (field.name == "id") " PRIMARY KEY" else ""
            "${field.name} $sqlType$pk"
        }
        val sql = "CREATE TABLE IF NOT EXISTS ${entity.table} ($fieldsSql)"
        jdbc.jdbcTemplate.execute(sql)
        println("[SOMNIA] Table ${entity.table} verified/created.")
    }

    fun findAll(): List<Map<String, Any?>> {
        val sql = "SELECT * FROM ${entity.table}"
        return jdbc.queryForList(sql, emptyMap<String, Any>())
    }
    
    fun save(data: Any): Any {
        val map = if (data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            data as MutableMap<String, Any?>
        } else {
            @Suppress("UNCHECKED_CAST")
            mapper.convertValue(data, Map::class.java) as MutableMap<String, Any?>
        }

        // Ensure ID is generated for new entities if type is UUID
        val idField = entity.fields.find { it.name == "id" }
        if (idField != null && map["id"] == null) {
            if (idField.type == "UUID") {
                map["id"] = UUID.randomUUID()
            }
        }

        val columns = map.keys.joinToString(", ")
        val placeholders = map.keys.joinToString(", ") { ":$it" }
        val sql = "INSERT INTO ${entity.table} ($columns) VALUES ($placeholders)"
        
        // Simple insert for MVP. 
        // In a real engine we would check for update (upsert) logic.
        jdbc.update(sql, map)
        return map
    }
    
    fun deleteById(id: Any) {
        val sql = "DELETE FROM ${entity.table} WHERE id = :id"
        jdbc.update(sql, mapOf("id" to id))
    }
}
