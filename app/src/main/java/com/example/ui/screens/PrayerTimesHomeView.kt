package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CachedPrayerTimes
import com.example.ui.viewmodel.PrayerUiState
import com.example.ui.viewmodel.PrayerViewModel

@Composable
fun PrayerTimesHomeView(
    uiState: PrayerUiState,
    viewModel: PrayerViewModel,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val times = uiState.prayerTimes
    val settings = uiState.settings

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header (Dates + Location)
        item {
            HeaderCard(
                cityName = times?.cityName ?: settings.cityName,
                countryName = times?.countryName ?: settings.countryName,
                hijriDate = times?.hijriDate ?: "Loading Hijri...",
                gregorianDate = times?.gregorianDate ?: "Loading Gregorian...",
                useGps = settings.useGps,
                onLocationModeChange = { viewModel.updateGpsMode(it) },
                onRefresh = { viewModel.refreshPrayerTimes() }
            )
        }

        // 2. Countdown and Progress Card
        if (times != null) {
            item {
                CountdownCard(
                    currentPrayer = uiState.currentPrayerName,
                    nextPrayer = uiState.nextPrayerName,
                    nextPrayerTime = uiState.nextPrayerTime,
                    countdown = uiState.countdownText,
                    progress = uiState.progressToNext
                )
            }
        }

        // 3. Location Permission Check Banner
        if (settings.useGps && !uiState.locationPermissionGranted) {
            item {
                PermissionWarningCard(onRequestLocationPermission = onRequestLocationPermission)
            }
        }

        // 4. Five Daily Prayers List
        if (times != null) {
            item {
                Text(
                    text = "Daily Prayer Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val prayers = listOf(
                PrayerInfo("Fajr", times.fajr, Icons.Outlined.LightMode, settings.notificationFajr),
                PrayerInfo("Sunrise", times.sunrise, Icons.Outlined.WbSunny, false, isSelectableNotification = false),
                PrayerInfo("Dhuhr", times.dhuhr, Icons.Filled.WbSunny, settings.notificationDhuhr),
                PrayerInfo("Asr", times.asr, Icons.Outlined.WbTwilight, settings.notificationAsr),
                PrayerInfo("Maghrib", times.maghrib, Icons.Filled.WbTwilight, settings.notificationMaghrib),
                PrayerInfo("Isha", times.isha, Icons.Outlined.NightsStay, settings.notificationIsha)
            )

            prayers.forEach { prayer ->
                item {
                    val isCurrent = uiState.currentPrayerName == prayer.name ||
                            (prayer.name == "Sunrise" && uiState.currentPrayerName == "Sunrise (Shuruq)")
                    val isNext = uiState.nextPrayerName == prayer.name

                    PrayerRowCard(
                        prayer = prayer,
                        isCurrent = isCurrent,
                        isNext = isNext,
                        format24h = settings.timeFormat24h,
                        onNotificationToggle = { enabled ->
                            viewModel.updatePrayerNotification(prayer.name, enabled)
                        }
                    )
                }
            }
        } else if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.error != null) {
            item {
                ErrorCard(error = uiState.error, onRetry = { viewModel.refreshPrayerTimes() })
            }
        }
    }
}

data class PrayerInfo(
    val name: String,
    val time: String,
    val icon: ImageVector,
    val notificationEnabled: Boolean,
    val isSelectableNotification: Boolean = true
)

@Composable
fun HeaderCard(
    cityName: String,
    countryName: String,
    hijriDate: String,
    gregorianDate: String,
    useGps: Boolean,
    onLocationModeChange: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("header_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (useGps) Icons.Default.MyLocation else Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cityName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (useGps) "Auto-Detect (GPS)" else countryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh prayer times",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = gregorianDate.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = hijriDate.uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun CountdownCard(
    currentPrayer: String,
    nextPrayer: String,
    nextPrayerTime: String,
    countdown: String,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "Progress")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("countdown_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow radial background
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "UPCOMING: $nextPrayer",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val parts = countdown.split(":")
                    if (parts.size >= 2) {
                        Text(
                            text = parts[0],
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-1).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Light
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Text(
                            text = parts[1],
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-1).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (parts.size == 3) {
                            Text(
                                text = parts[2] + "s",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                            )
                        }
                    } else {
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Light
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "$nextPrayer begins at $nextPrayerTime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = "Currently in $currentPrayer time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun PrayerRowCard(
    prayer: PrayerInfo,
    isCurrent: Boolean,
    isNext: Boolean,
    format24h: Boolean,
    onNotificationToggle: (Boolean) -> Unit
) {
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        isNext -> MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val borderModifier = if (isCurrent) {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            shape = RoundedCornerShape(18.dp)
        )
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .testTag("${prayer.name.lowercase()}_card"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = if (isCurrent) 18.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = prayer.icon,
                        contentDescription = prayer.name,
                        tint = if (isCurrent) Color.White else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = prayer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) Color.White else contentColor
                    )
                    if (isCurrent) {
                        Text(
                            text = "CURRENT PRAYER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    } else if (isNext) {
                        Text(
                            text = "Upcoming",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val formattedTime = if (format24h) {
                    prayer.time
                } else {
                    formatTo12h(prayer.time)
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else contentColor,
                    fontSize = if (isCurrent) 18.sp else 16.sp
                )

                if (prayer.isSelectableNotification) {
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = { onNotificationToggle(!prayer.notificationEnabled) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (prayer.notificationEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = "Toggle notifications for ${prayer.name}",
                            tint = if (prayer.notificationEnabled) {
                                if (isCurrent) Color.White else MaterialTheme.colorScheme.primary
                            } else {
                                if (isCurrent) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionWarningCard(onRequestLocationPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Location Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "GPS auto-detection is enabled, but the app lacks location permissions. Please authorize location access or select a city manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestLocationPermission,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Grant Location Permission")
            }
        }
    }
}

@Composable
fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("Retry Connection")
            }
        }
    }
}

private fun formatTo12h(time24: String): String {
    try {
        val parts = time24.split(":")
        if (parts.size != 2) return time24
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val suffix = if (hours >= 12) "PM" else "AM"
        val displayHours = when {
            hours == 0 -> 12
            hours > 12 -> hours - 12
            else -> hours
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", displayHours, minutes, suffix)
    } catch (e: Exception) {
        return time24
    }
}
