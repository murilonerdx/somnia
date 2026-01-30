package somnia.core

enum class NodeKind {
    DRIVE, AFFECT, TRACE, ARCHETYPE, SYMBOL, CONCEPT
}

data class Node(
    val kind: NodeKind,
    val value: String,
    var activation: Double = 0.0,
    val members: MutableSet<String> = mutableSetOf() // For Archetypes: list of child values
) {
    override fun toString(): String = "${kind.name}:$value(@${String.format("%.2f", activation)})"
}

data class Edge(
    val from: Node,
    val to: Node,
    val weight: Double,
    val decay: Double = 0.1
)

data class Proposal(
    val content: String,
    val score: Double, // Final utility
    val baseWeight: Double,
    val activation: Double,
    val args: List<Any?> = listOf(), // Arguments for the action
    val origin: List<Node> = listOf(), // Trace back to why this was proposed
    val trace: List<String> = listOf() // Human-readable explanation
)

class MemoryGraph {
    private val nodes = mutableMapOf<String, Node>()
    private val edges = mutableListOf<Edge>()

    fun getOrCreate(kind: NodeKind, value: String): Node {
        val key = "${kind.name}:$value"
        return nodes.getOrPut(key) { Node(kind, value) }
    }
    
    fun addArchetypeMember(archetypeName: String, memberValue: String) {
        val arch = getOrCreate(NodeKind.ARCHETYPE, archetypeName)
        arch.members.add(memberValue)
        // Auto-create implicit edge (strong association)
        addAssociation(NodeKind.ARCHETYPE, archetypeName, NodeKind.CONCEPT, memberValue, 0.9)
    }

    fun addAssociation(fromKind: NodeKind, fromValue: String, toKind: NodeKind, toValue: String, weight: Double) {
        val from = getOrCreate(fromKind, fromValue)
        val to = getOrCreate(toKind, toValue)
        edges.add(Edge(from, to, weight))
    }

    fun getLinks(node: Node): List<Edge> = edges.filter { it.from == node }
    
    fun getAllNodes(): Collection<Node> = nodes.values
    fun getAllEdges(): Collection<Edge> = edges
}
