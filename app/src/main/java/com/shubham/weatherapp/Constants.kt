package com.shubham.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {

    const val BASE_URL = "https://api.openweathermap.org/data/"
    const val APP_ID = "2c4e41878bdcb59d5f34d103a5b0af52"
    const val METRIC_SYSTEM = "metric"
    const val WEATHER_ICON_URL = "https://openweathermap.org/img/wn/"
    const val WEATHER_ICON_SIZE = "@4x.png"
    const val WEATHER_APP_PREFERENCE = "WeatherAppPreference"
    const val WEATHER_RESPONSE_DATA = "weather_response_data"


    /**
     * This function is used check whether the device is connected to the Internet or not.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }
        else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return  activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
        }

    }

}