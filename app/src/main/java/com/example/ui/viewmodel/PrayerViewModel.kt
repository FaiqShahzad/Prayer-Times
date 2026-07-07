package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.calculator.CalculationMethod
import com.example.data.calculator.HighLatitudeRule
import com.example.data.calculator.Madhab
import com.example.data.database.AppDatabase
import com.example.data.database.CachedPrayerTimes
import com.example.data.repository.City
import com.example.data.repository.CityDatabase
import com.example.data.repository.PrayerTimesRepository
import com.example.data.repository.UserSettings
import com.example.notification.PrayerNotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PrayerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val prayerTimes: CachedPrayerTimes? = null,
    val currentPrayerName: String = "",
    val nextPrayerName: String = "",
    val countdownText: String = "",
    val nextPrayerTime: String = "",
    val progressToNext: Float = 0f, // 0.0 to 1.0 representing how close the next prayer is
    val settings: UserSettings = UserSettings(),
    val locationPermissionGranted: Boolean = false,
    val manualCitySearchQuery: String = "",
    val searchedCities: List<City> = emptyList()
)

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = PrayerTimesRepository(
        cachedDao = db.cachedPrayerTimesDao(),
        settingsDao = db.userSettingsDao()
    )

    private val _uiState = MutableStateFlow(PrayerUiState())
    val uiState: StateFlow<PrayerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Observe user settings reactively
        viewModelScope.launch {
            _uiState.update { it.copy(searchedCities = CityDatabase.cities) }
            
            repository.userSettings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                refreshPrayerTimes()
                
                // Automatically schedule notifications whenever settings change
                PrayerNotificationScheduler.scheduleNextPrayerAlarms(
                    context = getApplication(),
                    repository = repository,
                    settings = settings
                )
            }
        }

        // Start real-time timer for current/next prayer and countdown updates
        startCountdownTimer()
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(locationPermissionGranted = granted) }
        if (granted && _uiState.value.settings.useGps) {
            // we will request location from MainActivity and update it
        }
    }

    fun updateGpsMode(useGps: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("use_gps", useGps.toString())
            if (!useGps) {
                // Default back to selected city or Makkah
                repository.saveSetting("city_name", "Makkah")
                repository.saveSetting("country_name", "Saudi Arabia")
                repository.saveSetting("latitude", "21.3891")
                repository.saveSetting("longitude", "39.8579")
            }
        }
    }

    fun selectManualCity(city: City) {
        viewModelScope.launch {
            repository.saveSetting("use_gps", "false")
            repository.saveSetting("city_name", city.name)
            repository.saveSetting("country_name", city.country)
            repository.saveSetting("latitude", city.latitude.toString())
            repository.saveSetting("longitude", city.longitude.toString())
            _uiState.update { it.copy(manualCitySearchQuery = "") }
        }
    }

    fun updateCoordinates(latitude: Double, longitude: Double, cityName: String, countryName: String) {
        viewModelScope.launch {
            repository.saveSetting("latitude", latitude.toString())
            repository.saveSetting("longitude", longitude.toString())
            repository.saveSetting("city_name", cityName)
            repository.saveSetting("country_name", countryName)
        }
    }

    fun updateCalculationMethod(method: CalculationMethod) {
        viewModelScope.launch {
            repository.saveSetting("calculation_method", method.id.toString())
        }
    }

    fun updateAsrMadhab(madhab: Madhab) {
        viewModelScope.launch {
            repository.saveSetting("asr_madhab", madhab.id.toString())
        }
    }

    fun updateHighLatitudeRule(rule: HighLatitudeRule) {
        viewModelScope.launch {
            repository.saveSetting("high_latitude_rule", rule.id.toString())
        }
    }

    fun updateTimeFormat(format24h: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("time_format_24h", format24h.toString())
        }
    }

    fun updateGlobalNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("global_notifications_enabled", enabled.toString())
        }
    }

    fun updatePrayerNotification(prayerName: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("notification_${prayerName.lowercase()}", enabled.toString())
        }
    }

    fun updateSoundSettings(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("sound_enabled", enabled.toString())
        }
    }

    fun updateVibrationSettings(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("vibration_enabled", enabled.toString())
        }
    }

    fun updateUseOfflineCalculator(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("use_offline_calculator", enabled.toString())
        }
    }

    fun onCitySearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isEmpty()) {
                CityDatabase.cities
            } else {
                CityDatabase.cities.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.country.contains(query, ignoreCase = true) ||
                    it.state.contains(query, ignoreCase = true)
                }
            }
            state.copy(manualCitySearchQuery = query, searchedCities = filtered)
        }
    }

    fun refreshPrayerTimes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val settings = _uiState.value.settings
                val today = Calendar.getInstance()
                val times = repository.getPrayerTimes(today, settings)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        prayerTimes = times
                    )
                }
                updateCountdown()
            } catch (e: Exception) {
                Log.e("PrayerViewModel", "Error fetching prayer times", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to fetch prayer times. Please check your network or switch to local offline calculations."
                    )
                }
            }
        }
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                updateCountdown()
                delay(1000)
            }
        }
    }

    private suspend fun updateCountdown() {
        val state = _uiState.value
        val prayerTimes = state.prayerTimes ?: return
        val settings = state.settings

        val now = Calendar.getInstance()
        val nowMs = now.timeInMillis

        // Parse today's prayers
        val keys = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        val todayCalendarTimes = keys.mapNotNull { name ->
            val timeStr = when (name) {
                "Fajr" -> prayerTimes.fajr
                "Sunrise" -> prayerTimes.sunrise
                "Dhuhr" -> prayerTimes.dhuhr
                "Asr" -> prayerTimes.asr
                "Maghrib" -> prayerTimes.maghrib
                "Isha" -> prayerTimes.isha
                else -> ""
            }
            val cal = PrayerNotificationScheduler.parsePrayerTime(prayerTimes.dateStr, timeStr)
            if (cal != null) Pair(name, cal) else null
        }

        if (todayCalendarTimes.isEmpty()) return

        // Find current and next prayer
        var nextPair: Pair<String, Calendar>? = null
        var currentPair: Pair<String, Calendar>? = null

        // Sort by time
        val sortedToday = todayCalendarTimes.sortedBy { it.second.timeInMillis }

        for (i in sortedToday.indices) {
            val (name, cal) = sortedToday[i]
            if (cal.timeInMillis > nowMs) {
                nextPair = Pair(name, cal)
                // Current is the one before it, or Isha of yesterday if it is Fajr.
                currentPair = if (i > 0) sortedToday[i - 1] else sortedToday.last()
                break
            }
        }

        // If no prayer is in the future today, the next prayer is tomorrow's Fajr
        if (nextPair == null) {
            currentPair = sortedToday.last() // Isha
            // Fetch tomorrow's Fajr
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tomorrowTimes = repository.getPrayerTimes(tomorrow, settings)
            val tomFajrCal = PrayerNotificationScheduler.parsePrayerTime(tomorrowTimes.dateStr, tomorrowTimes.fajr)
            if (tomFajrCal != null) {
                nextPair = Pair("Fajr", tomFajrCal)
            }
        }

        if (nextPair != null && currentPair != null) {
            val nextTimeMs = nextPair.second.timeInMillis
            val currentTimeMs = currentPair.second.timeInMillis

            val diffMs = nextTimeMs - nowMs
            val totalIntervalMs = nextTimeMs - currentTimeMs

            val progress = if (totalIntervalMs > 0) {
                val p = (nowMs - currentTimeMs).toFloat() / totalIntervalMs
                p.coerceIn(0f, 1f)
            } else {
                0f
            }

            // Build countdown string: hh:mm:ss
            val totalSeconds = diffMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            val countdownStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

            // Format next prayer time
            val timeSdf = if (settings.timeFormat24h) {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            } else {
                SimpleDateFormat("hh:mm a", Locale.getDefault())
            }
            val formattedNextTime = timeSdf.format(nextPair.second.time)

            // Filter out "Sunrise" as a literal "prayer" name when highlighting current prayer
            // usually sunrise is displayed but the active prayer of sunrise is none (it's between sunrise and dhuhr).
            // Let's keep it clean: current prayer is Fajr until Sunrise, Sunrise until Dhuhr, etc.
            val finalCurrentName = if (currentPair.first == "Sunrise") "Sunrise (Shuruq)" else currentPair.first

            _uiState.update {
                it.copy(
                    currentPrayerName = finalCurrentName,
                    nextPrayerName = nextPair.first,
                    nextPrayerTime = formattedNextTime,
                    countdownText = countdownStr,
                    progressToNext = progress
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
