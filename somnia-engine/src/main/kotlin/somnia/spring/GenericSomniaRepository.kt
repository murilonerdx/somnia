package somnia.spring

import org.springframework.data.mongodb.core.MongoTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GenericSomniaRepository(
    private val collectionName: String,
    private val template: MongoTemplate? = null
) {
    private val inMemoryDb = ConcurrentHashMap<String, Map<String, Any?>>()

    fun save(entity: Map<String, Any?>): Map<String, Any?> {
        val mutableEntity = entity.toMutableMap()
        val id = if (mutableEntity["id"] != null) {
            mutableEntity["id"].toString()
        } else {
            UUID.randomUUID().toString()
        }
        mutableEntity["id"] = id
        mutableEntity["_id"] = id
        
        if (template != null) {
            try {
                template.save(mutableEntity, collectionName)
                return mutableEntity
            } catch (e: Exception) {
                println("[SOMNIA] Mongo Save Failed, falling back to memory: ${e.message}")
            }
        }
        
        inMemoryDb[id] = mutableEntity
        return mutableEntity
    }

    fun findAll(): List<Map<String, Any?>> {
        if (template != null) {
            try {
                val results = template.findAll(Map::class.java, collectionName)
                if (results.isNotEmpty()) return results as List<Map<String, Any?>>
            } catch (e: Exception) {
                println("[SOMNIA] Mongo FindAll Failed, checked memory.")
            }
        }
        return inMemoryDb.values.toList()
    }
}
