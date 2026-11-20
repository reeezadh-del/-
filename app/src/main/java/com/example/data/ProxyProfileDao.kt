package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.model.ProxyProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyProfileDao {
    @Query("SELECT * FROM proxy_profiles ORDER BY isSelected DESC, createdAt DESC")
    fun observeAllProfiles(): Flow<List<ProxyProfile>>

    @Query("SELECT * FROM proxy_profiles WHERE isSelected = 1 LIMIT 1")
    fun observeSelectedProfile(): Flow<ProxyProfile?>

    @Query("SELECT * FROM proxy_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfile(): ProxyProfile?

    @Query("SELECT * FROM proxy_profiles")
    suspend fun getAllProfilesOnce(): List<ProxyProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProxyProfile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProxyProfile>)

    @Query("UPDATE proxy_profiles SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE proxy_profiles SET isSelected = 1 WHERE id = :id")
    suspend fun markSelected(id: Long)

    @Transaction
    suspend fun selectProfile(id: Long) {
        clearSelection()
        markSelected(id)
    }

    @Query("UPDATE proxy_profiles SET latencyMs = :latencyMs, lastTestedAt = :testedAt WHERE id = :id")
    suspend fun updateLatency(id: Long, latencyMs: Long, testedAt: Long)

    @Query("DELETE FROM proxy_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM proxy_profiles WHERE subscriptionSource = :subUrl")
    suspend fun deleteBySubscription(subUrl: String)

    @Query("DELETE FROM proxy_profiles")
    suspend fun deleteAll()
}
