package com.example.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.api.AladhanApiClient
import com.example.data.calculator.*
import com.example.data.database.CachedPrayerTimes
import com.example.data.database.CachedPrayerTimesDao
import com.example.data.database.UserSetting
import com.example.data.database.UserSettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class ProviderPrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val hijriDate: String,
    val gregorianDate: String
)

interface PrayerTimesProvider {
    suspend fun getPrayerTimes(
        latitude: Double,
        longitude: Double,
        timezoneOffsetHours: Double,
        calendar: Calendar,
        method: CalculationMethod,
        madhab: Madhab,
        highLatitudeRule: HighLatitudeRule
    ): ProviderPrayerTimes
}

class LocalPrayerTimesProvider : PrayerTimesProvider {
    override suspend fun getPrayerTimes(
        latitude: Double,
        longitude: Double,
        timezoneOffsetHours: Double,
        calendar: Calendar,
        method: CalculationMethod,
        madhab: Madhab,
        highLatitudeRule: HighLatitudeRule
    ): ProviderPrayerTimes = withContext(Dispatchers.Default) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val times = PrayerTimesCalculator.calculate(
            latitude = latitude,
            longitude = longitude,
            timezoneOffsetHours = timezoneOffsetHours,
            year = year,
            month = month,
            day = day,
            method = method,
            madhab = madhab,
            highLatitudeRule = highLatitudeRule
        )

        val hijriStr = estimateHijriDate(year, month, day)
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
        val gregStr = sdf.format(calendar.time)

        ProviderPrayerTimes(
            fajr = times.fajr,
            sunrise = times.sunrise,
            dhuhr = times.dhuhr,
            asr = times.asr,
            maghrib = times.maghrib,
            isha = times.isha,
            hijriDate = hijriStr,
            gregorianDate = gregStr
        )
    }

    private fun estimateHijriDate(year: Int, month: Int, day: Int): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val gCal = java.time.LocalDate.of(year, month, day)
                val hCal = java.time.chrono.HijrahDate.from(gCal)
                val monthNames = arrayOf(
                    "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
                    "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
                    "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
                )
                val mVal = hCal.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
                val monthName = if (mVal in 1..12) monthNames[mVal - 1] else "Month $mVal"
                val hYear = hCal.get(java.time.temporal.ChronoField.YEAR)
                val hDay = hCal.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                return "$hDay $monthName $hYear AH"
            } catch (e: Exception) {
                // fall back to mathematical estimate
            }
        }

        var y = year
        var m = month
        var d = day
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = Math.floor(y / 100.0)
        val b = 2.0 - a + Math.floor(a / 4.0)
        val jd = Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5

        val l = jd - 1948440 + 10632
        val n = Math.floor((l - 1) / 10631.0).toInt()
        val lRemaining = l - 10631 * n + 354
        val jYear = (Math.floor((30 * lRemaining - 83) / 10631.0) + 30 * n).toInt()
        val jYearRemaining = lRemaining - Math.floor((10631 * (jYear - 30 * n) + 83) / 30.0)
        val jMonth = Math.floor((jYearRemaining + 30) / 29.5).toInt()
        val jDay = (jYearRemaining - Math.floor(29.5 * jMonth - 28.5) + 1).toInt()

        val hijriMonths = arrayOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
        val mName = if (jMonth in 1..12) hijriMonths[jMonth - 1] else "Month $jMonth"
        return "$jDay $mName $jYear AH"
    }
}

class ApiPrayerTimesProvider : PrayerTimesProvider {
    private val localProvider = LocalPrayerTimesProvider()

    override suspend fun getPrayerTimes(
        latitude: Double,
        longitude: Double,
        timezoneOffsetHours: Double,
        calendar: Calendar,
        method: CalculationMethod,
        madhab: Madhab,
        highLatitudeRule: HighLatitudeRule
    ): ProviderPrayerTimes = withContext(Dispatchers.IO) {
        try {
            val sdfApi = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            val dateStr = sdfApi.format(calendar.time)

            // Map method ID
            val apiMethod = method.id
            // Map school (0 standard/shafi, 1 hanafi)
            val apiSchool = madhab.id
            // Map high lat adjustment
            val apiHighLat = when (highLatitudeRule) {
                HighLatitudeRule.NONE -> 0
                HighLatitudeRule.MIDDLE_OF_NIGHT -> 1
                HighLatitudeRule.ONE_SEVENTH -> 2
                HighLatitudeRule.ANGLE_BASED -> 3
            }

            Log.d("ApiProvider", "Fetching Aladhan API: $dateStr, method=$apiMethod, school=$apiSchool")
            val response = AladhanApiClient.service.getTimings(
                dateStr = dateStr,
                latitude = latitude,
                longitude = longitude,
                method = apiMethod,
                school = apiSchool,
                latitudeAdjustmentMethod = apiHighLat
            )

            if (response.code == 200) {
                val timings = response.data.timings
                val hijri = response.data.date.hijri
                val hijriStr = "${hijri.day} ${hijri.month.en} ${hijri.year} AH"

                val gregorian = response.data.date.gregorian
                val sdfOutput = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
                val gregStr = sdfOutput.format(calendar.time)

                ProviderPrayerTimes(
                    fajr = cleanTime(timings.Fajr),
                    sunrise = cleanTime(timings.Sunrise),
                    dhuhr = cleanTime(timings.Dhuhr),
                    asr = cleanTime(timings.Asr),
                    maghrib = cleanTime(timings.Maghrib),
                    isha = cleanTime(timings.Isha),
                    hijriDate = hijriStr,
                    gregorianDate = gregStr
                )
            } else {
                throw Exception("API Error code: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("ApiProvider", "API Call failed, falling back to offline calculator", e)
            localProvider.getPrayerTimes(
                latitude, longitude, timezoneOffsetHours, calendar, method, madhab, highLatitudeRule
            )
        }
    }

    private fun cleanTime(raw: String): String {
        // Aladhan sometimes returns "05:12 (EEST)" or similar. Clean to "05:12"
        return raw.substringBefore(" ").trim()
    }
}

data class UserSettings(
    val useGps: Boolean = true,
    val latitude: Double = 21.3891, // default Makkah
    val longitude: Double = 39.8579,
    val cityName: String = "Makkah",
    val countryName: String = "Saudi Arabia",
    val calculationMethod: CalculationMethod = CalculationMethod.MWL,
    val asrMadhab: Madhab = Madhab.STANDARD,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.NONE,
    val timeFormat24h: Boolean = false,
    val globalNotificationsEnabled: Boolean = true,
    val notificationFajr: Boolean = true,
    val notificationDhuhr: Boolean = true,
    val notificationAsr: Boolean = true,
    val notificationMaghrib: Boolean = true,
    val notificationIsha: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val useOfflineCalculator: Boolean = false
)

class PrayerTimesRepository(
    private val cachedDao: CachedPrayerTimesDao,
    private val settingsDao: UserSettingsDao
) {
    private val localProvider = LocalPrayerTimesProvider()
    private val apiProvider = ApiPrayerTimesProvider()

    // Expose settings reactively
    val userSettings: Flow<UserSettings> = settingsDao.getAllSettingsFlow()
        .map { list ->
            val map = list.associate { it.key to it.value }
            UserSettings(
                useGps = map["use_gps"]?.toBoolean() ?: true,
                latitude = map["latitude"]?.toDoubleOrNull() ?: 21.3891,
                longitude = map["longitude"]?.toDoubleOrNull() ?: 39.8579,
                cityName = map["city_name"] ?: "Makkah",
                countryName = map["country_name"] ?: "Saudi Arabia",
                calculationMethod = CalculationMethod.fromId(map["calculation_method"]?.toIntOrNull() ?: 2),
                asrMadhab = Madhab.fromId(map["asr_madhab"]?.toIntOrNull() ?: 0),
                highLatitudeRule = HighLatitudeRule.fromId(map["high_latitude_rule"]?.toIntOrNull() ?: 0),
                timeFormat24h = map["time_format_24h"]?.toBoolean() ?: false,
                globalNotificationsEnabled = map["global_notifications_enabled"]?.toBoolean() ?: true,
                notificationFajr = map["notification_fajr"]?.toBoolean() ?: true,
                notificationDhuhr = map["notification_dhuhr"]?.toBoolean() ?: true,
                notificationAsr = map["notification_asr"]?.toBoolean() ?: true,
                notificationMaghrib = map["notification_maghrib"]?.toBoolean() ?: true,
                notificationIsha = map["notification_isha"]?.toBoolean() ?: true,
                soundEnabled = map["sound_enabled"]?.toBoolean() ?: true,
                vibrationEnabled = map["vibration_enabled"]?.toBoolean() ?: true,
                useOfflineCalculator = map["use_offline_calculator"]?.toBoolean() ?: false
            )
        }
        .flowOn(Dispatchers.IO)

    suspend fun saveSetting(key: String, value: String) {
        settingsDao.saveSetting(UserSetting(key, value))
    }

    suspend fun clearCache() {
        cachedDao.clearCache()
    }

    suspend fun getPrayerTimes(
        date: Calendar,
        settings: UserSettings
    ): CachedPrayerTimes = withContext(Dispatchers.IO) {
        val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = sdfKey.format(date.time)

        // Try reading from Room Cache
        val cached = cachedDao.getPrayerTimesForDate(dateStr)
        if (cached != null &&
            abs(cached.latitude - settings.latitude) < 0.05 &&
            abs(cached.longitude - settings.longitude) < 0.05 &&
            cached.method == settings.calculationMethod.id &&
            cached.school == settings.asrMadhab.id
        ) {
            Log.d("Repository", "Returning cached prayer times for $dateStr")
            return@withContext cached
        }

        // Cache miss -> Fetch from provider
        val provider = if (settings.useOfflineCalculator) localProvider else apiProvider
        val timezoneOffsetHours = date.timeZone.getOffset(date.timeInMillis) / 3600000.0

        Log.d("Repository", "Cache miss. Fetching for $dateStr from provider")
        val providerTimes = provider.getPrayerTimes(
            latitude = settings.latitude,
            longitude = settings.longitude,
            timezoneOffsetHours = timezoneOffsetHours,
            calendar = date,
            method = settings.calculationMethod,
            madhab = settings.asrMadhab,
            highLatitudeRule = settings.highLatitudeRule
        )

        val newCache = CachedPrayerTimes(
            dateStr = dateStr,
            latitude = settings.latitude,
            longitude = settings.longitude,
            method = settings.calculationMethod.id,
            school = settings.asrMadhab.id,
            fajr = providerTimes.fajr,
            sunrise = providerTimes.sunrise,
            dhuhr = providerTimes.dhuhr,
            asr = providerTimes.asr,
            maghrib = providerTimes.maghrib,
            isha = providerTimes.isha,
            hijriDate = providerTimes.hijriDate,
            gregorianDate = providerTimes.gregorianDate,
            cityName = settings.cityName,
            countryName = settings.countryName
        )

        // Save to cache
        cachedDao.insertPrayerTimes(newCache)
        newCache
    }
}
