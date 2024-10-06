package com.shubham.weatherapp.network

import com.shubham.weatherapp.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * An Interface which defines the HTTP operations Functions.
 */
interface WeatherService {

    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String,
        @Query("appid") appId: String
    ) : Call<WeatherResponse>

    /*
    * When we call this function, we want to run a call which will use our WeatherResponse data class as the response of this whole call.
    * So basically we want to have a WeatherResponse object as a result, which will be a JSON object, which will contain all of the different information
    * such as the coordinates, weather list, the base, the main, the visibility and so forth defined in WeatherResponse model class.
    * */
}