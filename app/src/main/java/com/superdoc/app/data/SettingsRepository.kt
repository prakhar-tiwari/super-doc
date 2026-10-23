package com.superdoc.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class FamilyHelper(val name: String, val phone: String) {
    /** WhatsApp wants the number with country code and no symbols. Indian 10-digit numbers get 91. */
    val whatsAppNumber: String
        get() {
            val digits = phone.filter { it.isDigit() }.trimStart('0')
            return if (digits.length == 10) "91$digits" else digits
        }
}

data class Settings(
    val languageChosen: Boolean = false,
    val tourOffered: Boolean = false,
    val simpleMode: Boolean = true,
    val textScale: Float = 1f,
    val family: FamilyHelper? = null,
    val voiceOfflineOnly: Boolean = true,
    val activeModelId: String? = null,
    /** The AI helper is fetched from Play once by itself; after that only when the user asks. */
    val aiAutoSetupDone: Boolean = false,
    val signatureName: String = "",
    val noteColors: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val languageChosen = booleanPreferencesKey("language_chosen")
        val tourOffered = booleanPreferencesKey("tour_offered")
        val simpleMode = booleanPreferencesKey("simple_mode")
        val textScale = floatPreferencesKey("text_scale")
        val familyName = stringPreferencesKey("family_name")
        val familyPhone = stringPreferencesKey("family_phone")
        val voiceOfflineOnly = booleanPreferencesKey("voice_offline_only")
        val activeModelId = stringPreferencesKey("active_model_id")
        val aiAutoSetupDone = booleanPreferencesKey("ai_auto_setup_done")
        val signatureName = stringPreferencesKey("signature_name")
        val noteColors = booleanPreferencesKey("note_colors")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val familyPhone = p[Keys.familyPhone]
        Settings(
            languageChosen = p[Keys.languageChosen] ?: false,
            tourOffered = p[Keys.tourOffered] ?: false,
            simpleMode = p[Keys.simpleMode] ?: true,
            textScale = p[Keys.textScale] ?: 1f,
            family = if (familyPhone.isNullOrBlank()) null else FamilyHelper(p[Keys.familyName].orEmpty(), familyPhone),
            voiceOfflineOnly = p[Keys.voiceOfflineOnly] ?: true,
            activeModelId = p[Keys.activeModelId],
            aiAutoSetupDone = p[Keys.aiAutoSetupDone] ?: false,
            signatureName = p[Keys.signatureName].orEmpty(),
            noteColors = p[Keys.noteColors] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setLanguageChosen() = context.dataStore.edit { it[Keys.languageChosen] = true }
    suspend fun setTourOffered() = context.dataStore.edit { it[Keys.tourOffered] = true }
    suspend fun setSimpleMode(on: Boolean) = context.dataStore.edit { it[Keys.simpleMode] = on }
    suspend fun setTextScale(scale: Float) = context.dataStore.edit { it[Keys.textScale] = scale }
    suspend fun setNoteColors(on: Boolean) = context.dataStore.edit { it[Keys.noteColors] = on }
    suspend fun setVoiceOfflineOnly(on: Boolean) = context.dataStore.edit { it[Keys.voiceOfflineOnly] = on }
    suspend fun setSignatureName(name: String) = context.dataStore.edit { it[Keys.signatureName] = name.trim() }

    suspend fun setAiAutoSetupDone() = context.dataStore.edit { it[Keys.aiAutoSetupDone] = true }

    suspend fun setActiveModel(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.activeModelId) else it[Keys.activeModelId] = id
    }

    suspend fun setFamily(helper: FamilyHelper?) = context.dataStore.edit {
        if (helper == null) {
            it.remove(Keys.familyName); it.remove(Keys.familyPhone)
        } else {
            it[Keys.familyName] = helper.name.trim(); it[Keys.familyPhone] = helper.phone.trim()
        }
    }
}
