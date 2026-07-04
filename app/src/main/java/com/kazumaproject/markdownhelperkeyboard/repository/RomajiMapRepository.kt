package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapDao
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.DefaultRomajiToKanaMap
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RomajiMapRepository @Inject constructor(
    private val romajiMapDao: RomajiMapDao
) {

    /**
     * Get all saved Romaji maps as a Flow.
     */
    fun getAllMaps() = romajiMapDao.getAllMaps()

    /**
     * Get the currently active Romaji map as a Flow.
     */
    fun getActiveMap() = romajiMapDao.getActiveMap()

    /**
     * Get a specific Romaji map by its ID as a Flow.
     */
    fun getMapById(id: Long) = romajiMapDao.getMapById(id)

    /**
     * Insert a new Romaji map into the database.
     */
    suspend fun insert(map: RomajiMapEntity) = romajiMapDao.insert(map)

    /**
     * Insert a list of Romaji maps, typically used for importing.
     */
    suspend fun insertAll(maps: List<RomajiMapEntity>) = romajiMapDao.insertAll(maps)

    /**
     * Update an existing Romaji map.
     */
    suspend fun update(map: RomajiMapEntity) = romajiMapDao.update(map)

    /**
     * Delete a Romaji map.
     */
    suspend fun delete(map: RomajiMapEntity) = romajiMapDao.delete(map)

    /**
     * Set a specific map as active, deactivating all others.
     */
    suspend fun setActiveMap(mapId: Long) = romajiMapDao.setActiveMap(mapId)

    /**
     * Finds the non-deletable (default) map and updates its data with the
     * latest hardcoded default map data. This can be used to refresh
     * the default settings after an app update.
     */
    suspend fun updateDefaultMap() {
        if (romajiMapDao.count() == 0) return
        val nonDeletableMap = romajiMapDao.getNonDeletableMap()
        Timber.d("updateDefaultMap: ${nonDeletableMap?.mapData?.size}")
        if (nonDeletableMap != null) {
            if (nonDeletableMap.mapData.size != 312){
                val updatedMap = nonDeletableMap.copy(
                    mapData = getDefaultMapData()
                )
                romajiMapDao.update(updatedMap)
                Timber.d("Default Romaji map has been successfully updated.")
            }
        } else {
            Timber.w("Could not find a non-deletable map to update.")
        }
    }

    /**
     * Checks if any maps exist. If not, creates and inserts the
     * non-deletable default map and sets it as active.
     */
    suspend fun createDefaultMapIfNotExist() {
        if (romajiMapDao.count() == 0) {
            val defaultMap = RomajiMapEntity(
                name = "デフォルト",
                mapData = getDefaultMapData(),
                isActive = true,
                isDeletable = false
            )
            romajiMapDao.insert(defaultMap)
        }
    }

    /**
     * Returns the hardcoded default Romaji-to-Kana conversion map.
     */
    fun getDefaultMapData(): Map<String, Pair<String, Int>> = DefaultRomajiToKanaMap.data
}
