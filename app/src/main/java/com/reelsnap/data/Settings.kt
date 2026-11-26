package com.reelsnap.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reelsnap.TargetApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ImageFormat { JPEG, PNG }

data class Settings(
    val enabled: Boolean = true,
    val waitMs: Long = 2000,
    val shots: Int = 1,
    val gapMs: Long = 2000,
    val jitter: Boolean = true,
    /** 0 = unlimited. */
    val maxReels: Int = 0,
    /** 0 = unlimited. */
    val maxMinutes: Int = 0,
    val format: ImageFormat = ImageFormat.JPEG,
    val enabledApps: Set<String> = TargetApp.allPackages,
    val feedback: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val waitMs = longPreferencesKey("wait_ms")
        val shots = intPreferencesKey("shots")
        val gapMs = longPreferencesKey("gap_ms")
        val jitter = booleanPreferencesKey("jitter")
        val maxReels = intPreferencesKey("max_reels")
        val maxMinutes = intPreferencesKey("max_minutes")
        val format = stringPreferencesKey("format")
        val enabledApps = stringSetPreferencesKey("enabled_apps")
        val feedback = booleanPreferencesKey("feedback")
        val lastImageUri = stringPreferencesKey("last_image_uri")
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            enabled = this[Keys.enabled] ?: d.enabled,
            waitMs = this[Keys.waitMs] ?: d.waitMs,
            shots = this[Keys.shots] ?: d.shots,
            gapMs = this[Keys.gapMs] ?: d.gapMs,
            jitter = this[Keys.jitter] ?: d.jitter,
            maxReels = this[Keys.maxReels] ?: d.maxReels,
            maxMinutes = this[Keys.maxMinutes] ?: d.maxMinutes,
            format = this[Keys.format]?.let { runCatching { ImageFormat.valueOf(it) }.getOrNull() } ?: d.format,
            enabledApps = this[Keys.enabledApps] ?: d.enabledApps,
            feedback = this[Keys.feedback] ?: d.feedback,
        )
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    val lastImageUri: Flow<String?> = context.dataStore.data.map { it[Keys.lastImageUri] }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[Keys.enabled] = s.enabled
            p[Keys.waitMs] = s.waitMs.coerceIn(0, 60_000)
            p[Keys.shots] = s.shots.coerceIn(1, 20)
            p[Keys.gapMs] = s.gapMs.coerceIn(0, 60_000)
            p[Keys.jitter] = s.jitter
            p[Keys.maxReels] = s.maxReels.coerceAtLeast(0)
            p[Keys.maxMinutes] = s.maxMinutes.coerceAtLeast(0)
            p[Keys.format] = s.format.name
            p[Keys.enabledApps] = s.enabledApps
            p[Keys.feedback] = s.feedback
        }
    }

    suspend fun setLastImageUri(uri: String) {
        context.dataStore.edit { it[Keys.lastImageUri] = uri }
    }
}
