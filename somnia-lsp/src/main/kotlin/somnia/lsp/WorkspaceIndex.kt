package somnia.lsp

import java.util.concurrent.ConcurrentHashMap
import somnia.lang.EntityDecl
import somnia.lang.RepoDecl

/**
 * Maintains a global index of definitions across the entire workspace.
 * Used for cross-file validation and navigation.
 */
class WorkspaceIndex {
    // URI -> List of Entity Declarations
    private val entitiesByFile = ConcurrentHashMap<String, List<EntityDecl>>()
    
    // URI -> List of Repo Declarations
    private val reposByFile = ConcurrentHashMap<String, List<RepoDecl>>()
    
    // URI -> List of Action Declarations
    private val actionsByFile = ConcurrentHashMap<String, List<somnia.lang.ActionDecl>>()
    
    // URI -> List of Intent definitions
    private val intentsByFile = ConcurrentHashMap<String, List<String>>()

    fun update(
        uri: String, 
        entities: List<EntityDecl>, 
        repos: List<RepoDecl>,
        actions: List<somnia.lang.ActionDecl>,
        intents: List<String>
    ) {
        entitiesByFile[uri] = entities
        reposByFile[uri] = repos
        actionsByFile[uri] = actions
        intentsByFile[uri] = intents
    }

    fun remove(uri: String) {
        entitiesByFile.remove(uri)
        reposByFile.remove(uri)
        actionsByFile.remove(uri)
        intentsByFile.remove(uri)
    }

    fun getAllEntities(): List<EntityDecl> = entitiesByFile.values.flatten()
    fun findEntity(name: String): EntityDecl? = getAllEntities().find { it.name == name }
    
    fun getAllRepos(): List<RepoDecl> = reposByFile.values.flatten()
    fun findRepo(name: String): RepoDecl? = getAllRepos().find { it.name == name }
    
    fun findAction(name: String): somnia.lang.ActionDecl? = actionsByFile.values.flatten().find { it.name == name }
    
    fun hasIntent(name: String): Boolean = intentsByFile.values.flatten().contains(name)
}
