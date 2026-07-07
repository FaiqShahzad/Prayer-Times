package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.database.CachedPrayerTimes
import com.example.data.repository.PrayerTimesRepository
import com.example.data.repository.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PrayerNotificationScheduler {

    private const val TAG = "PrayerScheduler"

    fun scheduleNextPrayerAlarms(
        context: Context,
        repository: PrayerTimesRepository,
        settings: UserSettings
    ) {
        if (!settings.globalNotificationsEnabled) {
            cancelAllAlarms(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

        // We can schedule both today's remaining prayers and tomorrow's first few prayers
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todayTimes = repository.getPrayerTimes(today, settings)
                val tomorrowTimes = repository.getPrayerTimes(tomorrow, settings)

                val nowMs = System.currentTimeMillis()

                val prayersToSchedule = listOf(
                    Triple("Fajr", todayTimes.fajr, todayTimes.dateStr),
                    Triple("Dhuhr", todayTimes.dhuhr, todayTimes.dateStr),
                    Triple("Asr", todayTimes.asr, todayTimes.dateStr),
                    Triple("Maghrib", todayTimes.maghrib, todayTimes.dateStr),
                    Triple("Isha", todayTimes.isha, todayTimes.dateStr)
                )

                val tomorrowPrayers = listOf(
                    Triple("Fajr", tomorrowTimes.fajr, tomorrowTimes.dateStr),
                    Triple("Dhuhr", tomorrowTimes.dhuhr, tomorrowTimes.dateStr),
                    Triple("Asr", tomorrowTimes.asr, tomorrowTimes.dateStr),
                    Triple("Maghrib", tomorrowTimes.maghrib, tomorrowTimes.dateStr),
                    Triple("Isha", tomorrowTimes.isha, tomorrowTimes.dateStr)
                )

                // For each prayer, if today's is in the future, schedule today's. Otherwise schedule tomorrow's.
                prayersToSchedule.forEach { (name, timeStr, dateStr) ->
                    val isEnabledForPrayer = when (name.lowercase()) {
                        "fajr" -> settings.notificationFajr
                        "dhuhr" -> settings.notificationDhuhr
                        "asr" -> settings.notificationAsr
                        "maghrib" -> settings.notificationMaghrib
                        "isha" -> settings.notificationIsha
                        else -> true
                    }

                    if (!isEnabledForPrayer) {
                        cancelAlarm(context, name)
                        return@forEach
                    }

                    val cal = parsePrayerTime(dateStr, timeStr) ?: return@forEach
                    val targetMs = cal.timeInMillis

                    if (targetMs > nowMs) {
                        scheduleAlarm(context, alarmManager, name, targetMs, settings.cityName)
                    } else {
                        // Schedule tomorrow's instead
                        val tomPair = tomorrowPrayers.find { it.first == name } ?: return@forEach
                        val tomCal = parsePrayerTime(tomPair.third, tomPair.second) ?: return@forEach
                        scheduleAlarm(context, alarmManager, name, tomCal.timeInMillis, settings.cityName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule alarms", e)
            }
        }
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        prayerName: String,
        triggerAtMillis: Long,
        cityName: String
    ) {
        val requestCode = getRequestCode(prayerName)
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
            putExtra("CITY_NAME", cityName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.cancel(pendingIntent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            val formatted = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(Date(triggerAtMillis))
            Log.d(TAG, "Scheduled alarm for $prayerName at $formatted")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm for $prayerName", e)
        }
    }

    private fun cancelAlarm(context: Context, prayerName: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = getRequestCode(prayerName)
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for $prayerName")
        }
    }

    fun cancelAllAlarms(context: Context) {
        listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").forEach {
            cancelAlarm(context, it)
        }
    }

    private fun getRequestCode(prayerName: String): Int {
        return when (prayerName.lowercase()) {
            "fajr" -> 10001
            "dhuhr" -> 10002
            "asr" -> 10003
            "maghrib" -> 10004
            "isha" -> 10005
            else -> 10000
        }
    }

    fun parsePrayerTime(dateStr: String, timeStr: String): Calendar? {
        try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null

            val dateParts = dateStr.split("-")
            if (dateParts.size != 3) return null
            val year = dateParts[0].toIntOrNull() ?: return null
            val month = dateParts[1].toIntOrNull() ?: return null
            val day = dateParts[2].toIntOrNull() ?: return null

            return Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) {
            return null
        }
    }
}
