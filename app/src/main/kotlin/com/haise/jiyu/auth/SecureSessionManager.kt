package com.haise.jiyu.auth

import com.haise.jiyu.security.SecureCredentialStore
import io.github.jan.supabase.gotrue.SessionManager
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SessionManager] pro Supabase Auth ukládající session (access/refresh token k účtu Jiyu)
 * přes [SecureCredentialStore] - tedy šifrovaně přes Android Keystore, stejně jako appka
 * už dělá u tracker tokenů (MAL/Kitsu/MangaUpdates). Bez tohohle by knihovna sama defaultně
 * spadla na [io.github.jan.supabase.gotrue.SettingsSessionManager], která session ukládá do
 * obyčejných nešifrovaných SharedPreferences.
 *
 * Přechod na tenhle SessionManager odhlásí existující session (starý formát/úložiště se
 * nečte) - u appky s jedním uživatelem přijatelná jednorázová cena, přihlášení samo o sobě
 * žádná data neztrácí (knihovna žije v Room, ne v session).
 */
@Singleton
class SecureSessionManager @Inject constructor(
    private val secureStore: SecureCredentialStore,
) : SessionManager {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        secureStore.set(KEY_SESSION, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession? {
        val raw = secureStore.get(KEY_SESSION) ?: return null
        return try {
            json.decodeFromString(UserSession.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteSession() {
        secureStore.remove(KEY_SESSION)
    }

    companion object {
        private const val KEY_SESSION = "supabase_user_session"
    }
}
