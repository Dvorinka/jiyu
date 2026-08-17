package com.haise.jiyu.di

import com.haise.jiyu.BuildConfig
import com.haise.jiyu.auth.SecureSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(secureSessionManager: SecureSessionManager): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            // Bez tohohle knihovna defaultně uklada session (access/refresh token k uctu
            // Jiyu) do obycejnych nesifrovanych SharedPreferences - viz SecureSessionManager.
            sessionManager = secureSessionManager
        }
        install(Postgrest)
    }
}
