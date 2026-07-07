package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_prayer_times")
data class CachedPrayerTimes(
    @PrimaryKey val dateStr: String, // "YYYY-MM-DD"
    val latitude: Double,
    val longitude: Double,
    val method: Int,
    val school: Int,
    val fajr: String,      // "HH:MM"
    val sunrise: String,   // "HH:MM"
    val dhuhr: String,     // "HH:MM"
    val asr: String,       // "HH:MM"
    val maghrib: String,   // "HH:MM"
    val isha: String,      // "HH:MM"
    val hijriDate: String, // e.g. "15 Shawwal 1447"
    val gregorianDate: String, // e.g. "2026-07-06"
    val cityName: String,
    val countryName: String
)

@Entity(tableName = "user_settings")
data class UserSetting(
    @PrimaryKey val key: String,
    val value: String
)
