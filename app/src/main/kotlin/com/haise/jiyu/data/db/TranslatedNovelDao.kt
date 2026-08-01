package com.haise.jiyu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.haise.jiyu.data.db.entity.TranslatedNovelEntity

@Dao
interface TranslatedNovelDao {
    @Query("SELECT * FROM translated_novel WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TranslatedNovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranslatedNovelEntity)

    @Query("DELETE FROM translated_novel")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM translated_novel")
    suspend fun count(): Int

    /**
     * Protějšek [TranslatedPageDao.deleteOlderThan]. Bez něj byly překlady novel jediná část
     * cache, která nikdy nezmizela: úklid při startu (viz JiyuApp) čistil pouze stránky,
     * takže tahle tabulka rostla, dokud uživatel neodinstaloval appku.
     */
    @Query("DELETE FROM translated_novel WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
