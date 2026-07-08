package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.calculator.*
import com.example.ui.viewmodel.PrayerUiState
import com.example.ui.viewmodel.PrayerViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsAndTimetableView(
    uiState: PrayerUiState,
    viewModel: PrayerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Settings, 1 = Monthly Timetable
    val tabs = listOf("Settings", "Monthly Timetable")

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                )
            }
        }

        when (selectedTab) {
            0 -> SettingsContent(uiState = uiState, viewModel = viewModel)
            1 -> MonthlyTimetableContent(uiState = uiState)
        }
    }
}

@Composable
fun SettingsContent(
    uiState: PrayerUiState,
    viewModel: PrayerViewModel
) {
    val settings = uiState.settings

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Location Source Setting
        item {
            Card(modifier = Modifier.fillMaxWidth().testTag("location_settings_card")) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Location Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto-Detect Location (GPS)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Detect coordinate updates automatically",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = settings.useGps,
                            onCheckedChange = { viewModel.updateGpsMode(it) },
                            modifier = Modifier.testTag("gps_toggle")
                        )
                    }

                    if (!settings.useGps) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Select City Manually", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.manualCitySearchQuery,
                            onValueChange = { viewModel.onCitySearchQueryChanged(it) },
                            label = { Text("Search city, region, or country") },
                            placeholder = { Text("e.g., Cairo, Riyadh, London...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            trailingIcon = {
                                if (uiState.manualCitySearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onCitySearchQueryChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("city_search_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Filtered cities list
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                        ) {
                            if (uiState.searchedCities.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No matching cities found.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    items(uiState.searchedCities) { city ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.selectManualCity(city) }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationCity,
                                                contentDescription = "City",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(city.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("${city.state}, ${city.country}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            }
                                        }
                                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Prayer Calculations Config
        item {
            Card(modifier = Modifier.fillMaxWidth().testTag("calculation_settings_card")) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Calculation Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Calculation Method dropdown replacement (Simple selector)
                    Text("Calculation Method", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    CalculationMethod.values().forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateCalculationMethod(method) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.calculationMethod == method,
                                onClick = { viewModel.updateCalculationMethod(method) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(method.methodName, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Madhab
                    Text("Asr Madhab School", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Madhab.values().forEach { madhab ->
                            Row(
                                modifier = Modifier
                                    .clickable { viewModel.updateAsrMadhab(madhab) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.asrMadhab == madhab,
                                    onClick = { viewModel.updateAsrMadhab(madhab) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (madhab == Madhab.STANDARD) "Standard (Shafi'i, Maliki, Hanbali)" else "Hanafi",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. High Latitude adjustment rule
                    Text("High Latitude Correction", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    HighLatitudeRule.values().forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateHighLatitudeRule(rule) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.highLatitudeRule == rule,
                                onClick = { viewModel.updateHighLatitudeRule(rule) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (rule) {
                                    HighLatitudeRule.NONE -> "No Correction"
                                    HighLatitudeRule.MIDDLE_OF_NIGHT -> "Middle of the Night method"
                                    HighLatitudeRule.ONE_SEVENTH -> "One Seventh of the Night method"
                                    HighLatitudeRule.ANGLE_BASED -> "Angle-Based method"
                                },
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. System Preferences
        item {
            Card(modifier = Modifier.fillMaxWidth().testTag("system_settings_card")) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "System Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 12h vs 24h
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Use 24-Hour Format", fontWeight = FontWeight.SemiBold)
                            Text("Display times in 24-hour style", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = settings.timeFormat24h,
                            onCheckedChange = { viewModel.updateTimeFormat(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Use Local Calculation offline-only mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Force Offline Calculator", fontWeight = FontWeight.SemiBold)
                            Text("Use mathematical math engine exclusively", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = settings.useOfflineCalculator,
                            onCheckedChange = { viewModel.updateUseOfflineCalculator(it) }
                        )
                    }
                }
            }
        }

        // 4. Notifications Preferences
        item {
            Card(modifier = Modifier.fillMaxWidth().testTag("notifications_settings_card")) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Notification Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Global notification switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Prayer Alerts Globally", fontWeight = FontWeight.SemiBold)
                            Text("Master toggle for all prayer reminders", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = settings.globalNotificationsEnabled,
                            onCheckedChange = { viewModel.updateGlobalNotifications(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Sounds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Notification Sound", fontWeight = FontWeight.SemiBold)
                            Text("Play default system sound on prayer alert", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = settings.soundEnabled,
                            onCheckedChange = { viewModel.updateSoundSettings(it) },
                            enabled = settings.globalNotificationsEnabled
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Vibration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Notification Vibration", fontWeight = FontWeight.SemiBold)
                            Text("Vibrate device when prayer alarm triggers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = settings.vibrationEnabled,
                            onCheckedChange = { viewModel.updateVibrationSettings(it) },
                            enabled = settings.globalNotificationsEnabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyTimetableContent(uiState: PrayerUiState) {
    val settings = uiState.settings
    val cal = Calendar.getInstance()

    val currentMonthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Generate monthly timetable offline in memory instantly!
    val monthlyTimes = remember(settings, daysInMonth) {
        val list = mutableListOf<Pair<Int, CalculatedPrayerTimes>>()
        val tempCal = Calendar.getInstance()
        val offset = cal.timeZone.getOffset(cal.timeInMillis) / 3600000.0

        for (day in 1..daysInMonth) {
            tempCal.set(Calendar.DAY_OF_MONTH, day)
            val times = PrayerTimesCalculator.calculate(
                latitude = settings.latitude,
                longitude = settings.longitude,
                timezoneOffsetHours = offset,
                year = tempCal.get(Calendar.YEAR),
                month = tempCal.get(Calendar.MONTH) + 1,
                day = day,
                method = settings.calculationMethod,
                madhab = settings.asrMadhab,
                highLatitudeRule = settings.highLatitudeRule
            )
            list.add(Pair(day, times))
        }
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$currentMonthName Timetable",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${settings.cityName}, ${settings.countryName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Header Row for the Table
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Day", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center, fontSize = 11.sp)
                    Text("Fajr", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, fontSize = 11.sp)
                    Text("Dhuhr", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, fontSize = 11.sp)
                    Text("Asr", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, fontSize = 11.sp)
                    Text("Maghrib", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center, fontSize = 11.sp)
                    Text("Isha", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, fontSize = 11.sp)
                }
            }
        }

        // Days Schedule List
        items(monthlyTimes) { (day, times) ->
            val isToday = Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == day

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isToday) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isToday) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$day",
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.8f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )

                    val fajrStr = if (settings.timeFormat24h) times.fajr else formatTo12hShort(times.fajr)
                    val dhuhrStr = if (settings.timeFormat24h) times.dhuhr else formatTo12hShort(times.dhuhr)
                    val asrStr = if (settings.timeFormat24h) times.asr else formatTo12hShort(times.asr)
                    val maghribStr = if (settings.timeFormat24h) times.maghrib else formatTo12hShort(times.maghrib)
                    val ishaStr = if (settings.timeFormat24h) times.isha else formatTo12hShort(times.isha)

                    Text(fajrStr, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    Text(dhuhrStr, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    Text(asrStr, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    Text(maghribStr, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    Text(ishaStr, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

private fun formatTo12hShort(time24: String): String {
    try {
        val parts = time24.split(":")
        if (parts.size != 2) return time24
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val displayHours = when {
            hours == 0 -> 12
            hours > 12 -> hours - 12
            else -> hours
        }
        return String.format(Locale.getDefault(), "%d:%02d", displayHours, minutes)
    } catch (e: Exception) {
        return time24
    }
}
