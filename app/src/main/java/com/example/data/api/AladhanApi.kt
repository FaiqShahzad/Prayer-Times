package com.example.data.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class AladhanResponse(
    val code: Int,
    val status: String,
    val data: AladhanData
)

data class AladhanData(
    val timings: Timings,
    val date: DateInfo
)

data class Timings(
    val Fajr: String,
    val Sunrise: String,
    val Dhuhr: String,
    val Asr: String,
    val Sunset: String,
    val Maghrib: String,
    val Isha: String
)

data class DateInfo(
    val readable: String,
    val timestamp: String,
    val hijri: HijriInfo,
    val gregorian: GregorianInfo
)

data class HijriInfo(
    val date: String,
    val day: String,
    val weekday: WeekdayInfo,
    val month: MonthInfo,
    val year: String
)

data class GregorianInfo(
    val date: String,
    val day: String,
    val weekday: WeekdayInfo,
    val month: MonthInfo,
    val year: String
)

data class WeekdayInfo(
    val en: String,
    val ar: String? = null
)

data class MonthInfo(
    val number: Int,
    val en: String,
    val ar: String? = null
)

interface AladhanApiService {
    @GET("v1/timings/{date}")
    suspend fun getTimings(
        @Path("date") dateStr: String, // "DD-MM-YYYY"
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("school") school: Int,
        @Query("latitudeAdjustmentMethod") latitudeAdjustmentMethod: Int
    ): AladhanResponse
}

object AladhanApiClient {
    private const val BASE_URL = "https://api.aladhan.com/"

    val service: AladhanApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AladhanApiService::class.java)
    }
}
