package com.example.data.calculator

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

enum class CalculationMethod(
    val id: Int,
    val methodName: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int = 0 // If non-zero, Isha is calculated as Maghrib + interval
) {
    MWL(2, "Muslim World League", 18.0, 17.0),
    ISNA(3, "Islamic Society of North America (ISNA)", 15.0, 15.0),
    UMM_AL_QURA(4, "Umm Al-Qura (Makkah)", 18.5, 0.0, 90), // Isha is always Maghrib + 90 min (120 in Ramadan, standardizing to 90 here)
    EGYPT(5, "Egyptian General Authority of Survey", 19.5, 17.5),
    KARACHI(1, "University of Islamic Sciences, Karachi", 18.0, 18.0),
    GULF(11, "Gulf Region (Maghrib + 90 min)", 19.5, 0.0, 90),
    NORTH_AMERICA(12, "North America (Fajr 15, Isha 15)", 15.0, 15.0);

    companion object {
        fun fromId(id: Int): CalculationMethod {
            return values().find { it.id == id } ?: MWL
        }
    }
}

enum class Madhab(val id: Int, val shadowFactor: Double) {
    STANDARD(0, 1.0), // Shafi'i, Maliki, Ja'fari, Hanbali
    HANAFI(1, 2.0);

    companion object {
        fun fromId(id: Int): Madhab {
            return values().find { it.id == id } ?: STANDARD
        }
    }
}

enum class HighLatitudeRule(val id: Int) {
    NONE(0),
    MIDDLE_OF_NIGHT(1),
    ONE_SEVENTH(2),
    ANGLE_BASED(3);

    companion object {
        fun fromId(id: Int): HighLatitudeRule {
            return values().find { it.id == id } ?: NONE
        }
    }
}

data class CalculatedPrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

object PrayerTimesCalculator {

    private fun sinDeg(deg: Double) = sin(Math.toRadians(deg))
    private fun cosDeg(deg: Double) = cos(Math.toRadians(deg))
    private fun tanDeg(deg: Double) = tan(Math.toRadians(deg))
    private fun asinDeg(x: Double) = Math.toDegrees(asin(x))
    private fun acosDeg(x: Double) = Math.toDegrees(acos(x))
    private fun atanDeg(x: Double) = Math.toDegrees(atan(x))

    fun calculate(
        latitude: Double,
        longitude: Double,
        timezoneOffsetHours: Double,
        year: Int,
        month: Int,
        day: Int,
        method: CalculationMethod,
        madhab: Madhab,
        highLatitudeRule: HighLatitudeRule
    ): CalculatedPrayerTimes {

        // 1. Calculate Julian Date
        val jd = getJulianDate(year, month, day)

        // 2. Sun coordinates for the day (at Dhuhr approx / noon)
        val d = jd - 2451545.0
        val g = (357.529 + 0.98560028 * d) % 360.0 // mean anomaly
        val q = (280.459 + 0.98564736 * d) % 360.0 // mean longitude
        val l = (q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2.0 * g)) % 360.0 // apparent longitude
        val e = 23.439 - 0.00000036 * d // obliquity of ecliptic

        // Right Ascension (normalized to 0..360)
        var ra = atanDeg(cosDeg(e) * sinDeg(l) / cosDeg(l))
        if (cosDeg(l) < 0) ra += 180.0
        ra = (ra + 360.0) % 360.0

        // Right Ascension in terms of hours
        val raHours = ra / 15.0

        // Declination
        val decl = asinDeg(sinDeg(e) * sinDeg(l))

        // Equation of Time (in hours)
        val qHours = q / 15.0
        var eqt = qHours - raHours
        if (eqt < -12.0) eqt += 24.0
        if (eqt > 12.0) eqt -= 24.0

        // 3. Solar Transit (Dhuhr Noon)
        // Noon in UTC is 12 - longitude/15 - eqt. Adding timezoneOffset gives local time.
        val dhuhrLocal = 12.0 + timezoneOffsetHours - (longitude / 15.0) - eqt

        // 4. Sunrise & Sunset (altitude of Sun is -0.833 degrees)
        val sunsetHourAngle = getHourAngle(-0.833, latitude, decl)
        val sunriseLocal = dhuhrLocal - sunsetHourAngle / 15.0
        val sunsetLocal = dhuhrLocal + sunsetHourAngle / 15.0

        // 5. Asr (shadow calculation)
        val angleDiff = abs(latitude - decl)
        val asrAltitude = atanDeg(1.0 / (madhab.shadowFactor + tanDeg(angleDiff)))
        val asrHourAngle = getHourAngle(asrAltitude, latitude, decl)
        val asrLocal = dhuhrLocal + asrHourAngle / 15.0

        // 6. Fajr & Isha Hour Angles (based on method's angles)
        var fajrHourAngle = getHourAngle(-method.fajrAngle, latitude, decl)
        var ishaHourAngle = if (method.ishaIntervalMinutes == 0) {
            getHourAngle(-method.ishaAngle, latitude, decl)
        } else {
            0.0
        }

        // Night duration for high latitude corrections
        val nightDuration = (24.0 - (sunsetLocal - sunriseLocal) + 24.0) % 24.0

        // Apply High Latitude rules if H is NaN or if rules are explicitly enabled and we are at high lat
        if (fajrHourAngle.isNaN() || ishaHourAngle.isNaN() || (abs(latitude) > 48.0 && highLatitudeRule != HighLatitudeRule.NONE)) {
            when (highLatitudeRule) {
                HighLatitudeRule.MIDDLE_OF_NIGHT -> {
                    val halfNight = nightDuration / 2.0
                    if (fajrHourAngle.isNaN() || abs(latitude) > 48.0) {
                        fajrHourAngle = (halfNight * 15.0)
                    }
                    if (method.ishaIntervalMinutes == 0 && (ishaHourAngle.isNaN() || abs(latitude) > 48.0)) {
                        ishaHourAngle = (halfNight * 15.0)
                    }
                }
                HighLatitudeRule.ONE_SEVENTH -> {
                    val portion = nightDuration / 7.0
                    if (fajrHourAngle.isNaN() || abs(latitude) > 48.0) {
                        fajrHourAngle = (portion * 15.0)
                    }
                    if (method.ishaIntervalMinutes == 0 && (ishaHourAngle.isNaN() || abs(latitude) > 48.0)) {
                        ishaHourAngle = (portion * 15.0)
                    }
                }
                HighLatitudeRule.ANGLE_BASED -> {
                    val fajrFraction = method.fajrAngle / 60.0
                    val ishaFraction = if (method.ishaIntervalMinutes == 0) method.ishaAngle / 60.0 else 0.0
                    if (fajrHourAngle.isNaN() || abs(latitude) > 48.0) {
                        fajrHourAngle = ((nightDuration * fajrFraction) * 15.0)
                    }
                    if (method.ishaIntervalMinutes == 0 && (ishaHourAngle.isNaN() || abs(latitude) > 48.0)) {
                        ishaHourAngle = ((nightDuration * ishaFraction) * 15.0)
                    }
                }
                HighLatitudeRule.NONE -> {
                    // Fallback if NaN to prevent crashes: use 1/7 rule
                    if (fajrHourAngle.isNaN()) fajrHourAngle = ((nightDuration / 7.0) * 15.0)
                    if (method.ishaIntervalMinutes == 0 && ishaHourAngle.isNaN()) ishaHourAngle = ((nightDuration / 7.0) * 15.0)
                }
            }
        }

        val fajrLocal = dhuhrLocal - fajrHourAngle / 15.0
        val maghribLocal = sunsetLocal // Maghrib is sunset local time

        val ishaLocal = if (method.ishaIntervalMinutes > 0) {
            maghribLocal + (method.ishaIntervalMinutes / 60.0)
        } else {
            dhuhrLocal + ishaHourAngle / 15.0
        }

        return CalculatedPrayerTimes(
            fajr = formatTime(fajrLocal),
            sunrise = formatTime(sunriseLocal),
            dhuhr = formatTime(dhuhrLocal),
            asr = formatTime(asrLocal),
            maghrib = formatTime(maghribLocal),
            isha = formatTime(ishaLocal)
        )
    }

    private fun getJulianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun getHourAngle(angle: Double, latitude: Double, declination: Double): Double {
        val num = sinDeg(angle) - sinDeg(latitude) * sinDeg(declination)
        val den = cosDeg(latitude) * cosDeg(declination)
        val cosH = num / den
        if (cosH < -1.0 || cosH > 1.0) {
            return Double.NaN
        }
        return acosDeg(cosH)
    }

    private fun formatTime(timeDecimal: Double): String {
        val hours24 = (timeDecimal + 24.0) % 24.0
        val totalMinutes = (hours24 * 60.0).roundToInt()
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }
}
