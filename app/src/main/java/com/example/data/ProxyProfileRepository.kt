package com.example.data

import com.example.model.ProxyProfile
import kotlinx.coroutines.flow.Flow

class ProxyProfileRepository(private val dao: ProxyProfileDao) {
    val allProfiles: Flow<List<ProxyProfile>> = dao.observeAllProfiles()
    val selectedProfile: Flow<ProxyProfile?> = dao.observeSelectedProfile()

    suspend fun getSelectedProfile(): ProxyProfile? = dao.getSelectedProfile()

    suspend fun getAllOnce(): List<ProxyProfile> = dao.getAllProfilesOnce()

    suspend fun insert(profile: ProxyProfile): Long = dao.insertProfile(profile)

    suspend fun insertAll(profiles: List<ProxyProfile>) = dao.insertProfiles(profiles)

    suspend fun select(id: Long) = dao.selectProfile(id)

    suspend fun clearSelection() = dao.clearSelection()

    suspend fun updateLatency(id: Long, latencyMs: Long) =
        dao.updateLatency(id, latencyMs, System.currentTimeMillis())

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun replaceSubscriptionProfiles(subUrl: String, profiles: List<ProxyProfile>) {
        dao.deleteBySubscription(subUrl)
        if (profiles.isNotEmpty()) {
            dao.insertProfiles(profiles)
        }
    }
}
