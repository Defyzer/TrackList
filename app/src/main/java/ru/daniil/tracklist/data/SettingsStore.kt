package ru.daniil.tracklist.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    val theme =
        context.settings.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[themeKey] ?: "system" }

    suspend fun setTheme(theme: String) {
        require(theme in listOf("system", "light", "dark"))
        context.settings.edit { it[themeKey] = theme }
    }
}
