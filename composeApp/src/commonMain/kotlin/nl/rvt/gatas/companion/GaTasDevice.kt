package nl.rvt.gatas.companion

import kotlinx.serialization.Serializable

@Serializable
data class GaTasDevice(
    val name: String,
    val identifier: String,
) {
    // Equality & hashcode based only on `identifier`
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GaTasDevice) return false
        return identifier == other.identifier
    }

    override fun hashCode(): Int = identifier.hashCode()
}
