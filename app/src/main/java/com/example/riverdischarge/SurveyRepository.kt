package com.example.riverdischarge

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Lenient so older/partial records still load instead of wiping the whole list. */
private val surveyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

private val SURVEYS_KEY = stringPreferencesKey("saved_surveys")

private val Context.surveyDataStore by preferencesDataStore(
    name = "surveys",
    produceMigrations = { context -> listOf(LegacyPrefsMigration(context)) }
)

private fun decodeSurveys(raw: String?): List<SavedSurvey> {
    if (raw.isNullOrBlank() || raw == "[]") return emptyList()
    return runCatching { surveyJson.decodeFromString<List<SavedSurvey>>(raw) }
        .getOrDefault(emptyList())
        .sortedByDescending { it.updatedAt }
}

/**
 * Storage layer behind a swappable interface: the UI never touches DataStore/JSON directly.
 * [surveys] is reactive, so saving or deleting refreshes the list without manual reloads.
 */
interface SurveyRepository {
    val surveys: Flow<List<SavedSurvey>>
    suspend fun save(survey: SavedSurvey)
    suspend fun delete(surveyId: String)
}

class DataStoreSurveyRepository(private val context: Context) : SurveyRepository {

    override val surveys: Flow<List<SavedSurvey>> =
        context.surveyDataStore.data.map { prefs -> decodeSurveys(prefs[SURVEYS_KEY]) }

    override suspend fun save(survey: SavedSurvey) {
        context.surveyDataStore.edit { prefs ->
            val others = decodeSurveys(prefs[SURVEYS_KEY]).filterNot { it.id == survey.id }
            val updated = (listOf(survey) + others).sortedByDescending { it.updatedAt }
            prefs[SURVEYS_KEY] = surveyJson.encodeToString(updated)
        }
    }

    override suspend fun delete(surveyId: String) {
        context.surveyDataStore.edit { prefs ->
            val updated = decodeSurveys(prefs[SURVEYS_KEY]).filterNot { it.id == surveyId }
            prefs[SURVEYS_KEY] = surveyJson.encodeToString(updated)
        }
    }
}

/**
 * One-time copy of the legacy SharedPreferences blob (`river_discharge_surveys`) into DataStore.
 * The old hand-written JSON is shape-compatible with kotlinx.serialization, so it is decoded
 * through the model (dropping anything unparseable) and re-encoded; the old key is then cleared.
 */
private const val LEGACY_PREFS = "river_discharge_surveys"
private const val LEGACY_KEY = "saved_surveys"

private class LegacyPrefsMigration(private val context: Context) : DataMigration<Preferences> {

    private fun legacyRaw(): String? =
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_KEY, null)
            ?.takeIf { it.isNotBlank() && it != "[]" }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[SURVEYS_KEY] == null && legacyRaw() != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val raw = legacyRaw() ?: return currentData
        val mutable = currentData.toMutablePreferences()
        mutable[SURVEYS_KEY] = surveyJson.encodeToString(decodeSurveys(raw))
        return mutable
    }

    override suspend fun cleanUp() {
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit().remove(LEGACY_KEY).apply()
    }
}
