package somnia.core.model

import somnia.core.value.SomniaValue

/**
 * Drive - A motivation or need that influences behavior.
 * Higher intensity = stronger influence on decisions.
 */
data class Drive(
    val name: String,
    val intensity: Double  // 0.0 to 1.0
) {
    init {
        require(intensity in 0.0..1.0) { "Drive intensity must be between 0.0 and 1.0" }
    }
}

/**
 * Affect - An emotional state that modifies behavior.
 * Valence: positive (joy) to negative (fear).
 */
data class Affect(
    val name: String,
    val valence: Double  // -1.0 to 1.0
) {
    init {
        require(valence in -1.0..1.0) { "Affect valence must be between -1.0 and 1.0" }
    }
    
    val isPositive: Boolean get() = valence > 0
    val isNegative: Boolean get() = valence < 0
    val isNeutral: Boolean get() = valence == 0.0
}

/**
 * Archetype - A behavioral pattern or personality trait.
 */
data class Archetype(
    val name: String,
    val traits: Map<String, SomniaValue> = emptyMap()
)

/**
 * Association - A learned connection between concepts.
 * Used for memory and pattern recognition.
 */
data class Association(
    val source: String,
    val target: String,
    val strength: Double  // 0.0 to 1.0
) {
    init {
        require(strength in 0.0..1.0) { "Association strength must be between 0.0 and 1.0" }
    }
}

/**
 * Fact - A piece of world state.
 * Facts are the "ground truth" the agent reasons about.
 */
data class Fact(
    val key: String,
    val value: SomniaValue
)

/**
 * Intent - An external trigger or request.
 * This is what starts an agent cycle.
 */
data class Intent(
    val name: String,
    val args: Map<String, SomniaValue> = emptyMap()
) {
    operator fun get(key: String): SomniaValue = args[key] ?: SomniaValue.SNull
}
