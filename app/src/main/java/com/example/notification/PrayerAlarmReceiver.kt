package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: return
        val cityName = intent.getStringExtra("CITY_NAME") ?: "your location"
        Log.d("PrayerAlarmReceiver", "Received alarm for: $prayerName in $cityName")

        val db = AppDatabase.getDatabase(context)
        val settingsDao = db.userSettingsDao()

        // Read settings in a coroutine to verify notification permission/enabled state before displaying
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsList = settingsDao.getAllSettingsFlow().firstOrNull() ?: emptyList()
                val map = settingsList.associate { it.key to it.value }

                val globalEnabled = map["global_notifications_enabled"]?.toBoolean() ?: true
                val prayerKey = "notification_${prayerName.lowercase()}"
                val prayerEnabled = map[prayerKey]?.toBoolean() ?: true
                val soundEnabled = map["sound_enabled"]?.toBoolean() ?: true
                val vibrationEnabled = map["vibration_enabled"]?.toBoolean() ?: true

                if (globalEnabled && prayerEnabled) {
                    showNotification(context, prayerName, cityName, soundEnabled, vibrationEnabled)
                }

                // Reschedule for next prayers
                val cachedDao = db.cachedPrayerTimesDao()
                val repository = com.example.data.repository.PrayerTimesRepository(cachedDao, settingsDao)
                val activeSettings = repository.userSettings.firstOrNull()
                if (activeSettings != null) {
                    PrayerNotificationScheduler.scheduleNextPrayerAlarms(context, repository, activeSettings)
                }
            } catch (e: Exception) {
                Log.e("PrayerAlarmReceiver", "Error processing alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        prayerName: String,
        cityName: String,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) {
        val channelId = "prayer_times_notifications_channel"
        val notificationId = when (prayerName.lowercase()) {
            "fajr" -> 1001
            "dhuhr" -> 1002
            "asr" -> 1003
            "maghrib" -> 1004
            "isha" -> 1005
            else -> 1000
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Prayer Reminders"
            val descriptionText = "Heads up notifications for daily Islamic prayers"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableLights(true)
                if (vibrationEnabled) {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                } else {
                    enableVibration(false)
                    vibrationPattern = null
                }

                if (soundEnabled) {
                    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(
                        soundUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    setSound(null, null)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = if (soundEnabled) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for $prayerName")
            .setContentText("It is now time for the $prayerName prayer in $cityName.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        if (soundUri != null) {
            builder.setSound(soundUri)
        }
        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 500, 200, 500))
        }

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e("PrayerAlarmReceiver", "Failed to show notification due to security exception", e)
        }
    }
}
