package nl.rvt.gatas.companion

import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nl.rvt.gatas.appContext

actual object AircraftCacheStore {
    private const val PREFERENCES_NAME = "gatas_aircraft_cache"
    private const val ENTRY_PREFIX = "aircraft_entry_"
    private val json = Json { ignoreUnknownKeys = true }

    actual suspend fun loadEntry(icaoHexCode: String): AircraftPickerEntry? = withContext(Dispatchers.IO) {
        val encoded = appContext
            .getSharedPreferences(PREFERENCES_NAME, 0)
            .getString("$ENTRY_PREFIX$icaoHexCode", null)
            ?: return@withContext null

        runCatching {
            json.decodeFromString<AircraftPickerEntry>(encoded)
        }.getOrNull()
    }

    actual suspend fun saveEntry(entry: AircraftPickerEntry) = withContext(Dispatchers.IO) {
        appContext.getSharedPreferences(PREFERENCES_NAME, 0).edit {
            putString(
                "$ENTRY_PREFIX${entry.icaoHexCode}",
                json.encodeToString(AircraftPickerEntry.serializer(), entry),
            )
        }
    }
}
