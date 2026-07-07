package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedPrayerTimesDao {
    @Query("SELECT * FROM cached_prayer_times WHERE dateStr = :dateStr LIMIT 1")
    suspend fun getPrayerTimesForDate(dateStr: String): CachedPrayerTimes?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrayerTimes(prayerTimes: CachedPrayerTimes)

    @Query("DELETE FROM cached_prayer_times")
    suspend fun clearCache()
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): UserSetting?

    @Query("SELECT * FROM user_settings WHERE `key` = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<UserSetting?>

    @Query("SELECT * FROM user_settings")
    fun getAllSettingsFlow(): Flow<List<UserSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: UserSetting)

    @Query("DELETE FROM user_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}
