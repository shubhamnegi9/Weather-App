package com.shubham.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.shubham.weatherapp.databinding.ActivityMainBinding
import com.shubham.weatherapp.models.WeatherResponse
import com.shubham.weatherapp.network.WeatherService
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


// OpenWeather API Link : https://openweathermap.org/api
/**
 * The useful link or some more explanation for this app you can checkout this link :
 * https://medium.com/@sasude9/basic-android-weather-app-6a7c0855caf4
 */
class MainActivity : AppCompatActivity() {

    // A fused location provider client variable which is used to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private val TAG = "MainActivity"
    private var customProgressDialog: Dialog? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize the Fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the SharedPreferences variable
        /**
         *  Context.MODE_PRIVATE:
         *  The created file can only be accessed by the calling application (or
         *  all applications sharing the same user ID).
         *  So it pretty much means that this shared preference information that we store on the phone should only
         *  be visible for this application and no other application.
         */
        sharedPreferences = getSharedPreferences(Constants.WEATHER_APP_PREFERENCE, Context.MODE_PRIVATE)

        /**
         *  Call the UI method to populate the data in
         *  the UI which are already stored in sharedPreferences earlier.
         *  At first run it will be blank.
         */
        setupUI()

        // Checking if location is enabled on the user's device
        if(!isLocationEnabled()) {
            Toast.makeText(this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT).show()

            // Intent to open the location settings of device
            // This will redirect you to settings from where you need to turn on the location provider
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
//            Toast.makeText(this, "Your location provider is already turned on.", Toast.LENGTH_SHORT).show()

            // Asking the location permission on runtime using Dexter Library
            Dexter.withContext(this)
                .withPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(object : MultiplePermissionsListener {                                // ctrl+shift+enter
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        Log.d(TAG, "onPermissionsChecked: areAllPermissionsGranted: " + report?.areAllPermissionsGranted())
                        if(report?.areAllPermissionsGranted() == true) {
                            requestLocationData()
                        }

                        Log.d(TAG, "onPermissionsChecked: report?.isAnyPermissionPermanentlyDenied: " + report?.isAnyPermissionPermanentlyDenied)
                        if(report?.isAnyPermissionPermanentlyDenied==true) {
                            Toast.makeText(this@MainActivity,
                                "You have denied location permission. Please allow as it is mandatory",
                                Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationaleDialogForPermissions()
                    }
                })
                .withErrorListener { error -> Log.i("Dexter: ", "onError: " + error.toString()) }
                .onSameThread()
                .check()
        }

        binding.ivRefresh.setOnClickListener {
            requestLocationData()
        }
    }

    private fun isLocationEnabled(): Boolean {
        // locationManager provides access to system location services
        val locationManager: LocationManager
                = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * A function used to show the alert dialog when the permissions are denied and need to allow it from settings app info.
     */
    private fun showRationaleDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton("GO TO SETTINGS") {
                _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("CANCEL") {
               dialog, _ -> dialog.dismiss()
            }
            .show()
    }

    /**
     * A function to request the current location updates using the fused location provider client.
     */
    @SuppressLint("MissingPermission")      // Suppressed the lint warning for handling missing permission here,
                                            // as we are already handling it before calling this function
    private fun requestLocationData() {
        // Showing progress dialog before requesting location data
        showCustomProgressDialog()

        // Create a LocationRequest object and set the desired parameters, such as update intervals, priority, and accuracy.
        // time in milliseconds, how often you want to get location updates
        val mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                                .setMaxUpdates(1)   // Limit to one update
                                .build()

        // Use the requestLocationUpdates() method of the FusedLocationProviderClient to start receiving location updates.
        // You'll need to provide a LocationCallback to handle the incoming location updates.
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }

    /**
     * A location callback object of fused location provider client where we will get the current location details.
     */
    private val mLocationCallback = object: LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.d(TAG, "Current Latitude: $latitude")

            val longitude = mLastLocation?.longitude
            Log.d(TAG, "Current Longitude: $longitude")

            // Calling the API to get weather details of the current location based on the latitude longitude
            if(latitude != null && longitude != null)
                getLocationWeatherDetails(latitude, longitude)
        }
    }

    /**
     * Function is used to get the weather details of the current location based on the latitude longitude
     */
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if(Constants.isNetworkAvailable(this)) {
//            Toast.makeText(this, "You are connected to internet", Toast.LENGTH_SHORT).show()

            /**
             *  Adding logging to retrofit calls so they can be seen in logcat for debugging
             */
            val logging = HttpLoggingInterceptor()
            // set your desired log level
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)
            val httpClient = OkHttpClient.Builder()
            // add your other interceptors …
            // add logging as last interceptor
            httpClient.addInterceptor(logging)

            /**
             * Add the built-in converter factory first. This prevents overriding its
             * behavior but also ensures correct behavior when using converters that consume all types.
             */
            val retrofitBuilder = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                /** Add converter factory for serialization and deserialization of objects. */
                /**
                 * Create an instance using a default {@link Gson} instance for conversion. Encoding to JSON and
                 * decoding from JSON (when no charset is specified by a header) will use UTF-8.
                 */
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient.build())
                /** Create the Retrofit instances. */
                .build()

            /**
             * Here we map the service interface in which we declares the end point and the API type
             *i.e GET, POST and so on along with the request parameter which are required.
             */
            val weatherService = retrofitBuilder.create(WeatherService::class.java)

            /** An invocation of a Retrofit method that sends a request to a web-server and returns a response.
             * Here we pass the required param in the service
             */
            val call: Call<WeatherResponse> = weatherService.getWeather(latitude, longitude, Constants.METRIC_SYSTEM, Constants.APP_ID)
            // Callback methods are executed using the Retrofit callback executor.
            call.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    hideCustomProgressDialog()
                    if(response.isSuccessful) {
                        /** The de-serialized response body of a successful response. */
                        val weatherResponse = response.body()
                        Log.d(TAG, "onResponse: $weatherResponse")

                        if(weatherResponse != null) {
                            // Here we have converted the model class in to Json String to store it in the SharedPreferences using GSON
                            val weatherResponseString = Gson().toJson(weatherResponse)
                            // Save the converted string to shared preferences
                            val editor = sharedPreferences.edit()
                            editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseString)
                            editor.apply()
                        }

                        setupUI()

                    }  else {
                        // If the response is not success then we check the response code.
                       val rc = response.code()
                       when(rc) {
                           400 -> Log.e(TAG, "onResponse: 400 Error: Bad Connection")
                           404 -> Log.e(TAG, "onResponse: 404 Error: Not Found")
                           else -> Log.e(TAG, "onResponse: Generic Error")
                       }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, throwable: Throwable) {
                    hideCustomProgressDialog()
                    Log.d(TAG, "onFailure: ${throwable.message.toString()}")
                }

            })


        } else {
            Toast.makeText(this, "No internet connection available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomProgressDialog() {
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.custom_progress_dialog)
        customProgressDialog?.setCanceledOnTouchOutside(false)
        customProgressDialog?.show()
    }

    private fun hideCustomProgressDialog() {
        customProgressDialog?.dismiss()
    }

    /**
     * Function is used to set the weatherResponse result in the UI elements.
     */
    private fun setupUI() {

        // Here we have got the latest stored response from the SharedPreference
        val weatherResponseData = sharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if(!weatherResponseData.isNullOrEmpty()) {
            // converted back to the data model object using GSON
            val weatherResponse = Gson().fromJson(weatherResponseData, WeatherResponse::class.java)

            for (i in weatherResponse.weather.indices) {
                binding.tvMain.text = weatherResponse.weather[i].main
                binding.tvMainDescription.text = weatherResponse.weather[i].description

                // Check https://openweathermap.org/weather-conditions for weather icon codes
                if(weatherResponse.weather[i].icon.endsWith('d')) {
                    binding.main.background = ContextCompat.getDrawable(this, R.drawable.weather_app_bg)
                } else {
                    binding.main.background = ContextCompat.getDrawable(this, R.drawable.weather_app_night_bg)
                }

                setWeatherIcons(weatherResponse.weather[i].icon)
            }
            val currentTemp = weatherResponse.main.temp.toString()
            val currentUnit = getUnit(application.resources.configuration.locales.toString())
            binding.tvTemp.text = currentTemp + currentUnit
            val humidity = weatherResponse.main.humidity.toString() + "% Humid"
            binding.tvHumidity.text = humidity
            val minTemp = weatherResponse.main.temp_min.toString() + currentUnit + " min"
            val maxTemp = weatherResponse.main.temp_max.toString() + currentUnit + " max"
            binding.tvMin.text = minTemp
            binding.tvMax.text = maxTemp
            binding.tvSpeed.text = weatherResponse.wind.speed.toString()
            binding.tvName.text = weatherResponse.name
            binding.tvCountry.text = weatherResponse.sys.country
            binding.tvSunriseTime.text =
                convertUnixTimestampToHumanTime(weatherResponse.sys.sunrise)
            binding.tvSunsetTime.text = convertUnixTimestampToHumanTime(weatherResponse.sys.sunset)
        }
    }

    /**
     * Function is used to get the temperature unit value.
     */
    private fun getUnit(locale: String): String {
        var tempUnit = "°C"
        if(locale == "US" || locale == "LR" || locale == "MM")
            tempUnit = "°F"
        return tempUnit
    }

    /**
     * The function is used to get the formatted time based on the Format and the LOCALE we pass to it.
     * Converter: https://www.epochconverter.com/
     */
    private fun convertUnixTimestampToHumanTime(time: Long): String {
        // Convert unix timestamp into Date object
        val date = Date(time*1000L) // Need to multiply with 1000 inorder to pass in Date() function
        Log.d(TAG, "convertUnixTimestampToHumanTime: $date")
        // Using SimpleDateFormat to parse the date in required format: "HH:mm" (for 24-hours format)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    /**
     *  Setting weather icons using Picasso Library
     *
     */
    private fun setWeatherIcons(iconCode: String) {
        val iconUrl = Constants.WEATHER_ICON_URL+iconCode+Constants.WEATHER_ICON_SIZE
        // Using Picasso Library to load the image using Image URL
        Picasso.get()
            .load(iconUrl)                          // URL of image to be load
            .placeholder(                           // Default image to show while loading
                when(iconCode) {
                    "01d" -> R.drawable.sunny
                    "02d" -> R.drawable.cloud
                    "03d" -> R.drawable.cloud
                    "04d" -> R.drawable.cloud
                    "04n" -> R.drawable.cloud
                    "10d" -> R.drawable.rain
                    "11d" -> R.drawable.storm
                    "13d" -> R.drawable.snowflake
                    "01n" -> R.drawable.cloud
                    "02n" -> R.drawable.cloud
                    "03n" -> R.drawable.cloud
                    "10n" -> R.drawable.cloud
                    "11n" -> R.drawable.rain
                    "13n" -> R.drawable.snowflake
                    else -> R.drawable.snowflake
                }
            )
            .into(binding.ivMain)
    }

    override fun onDestroy() {
        customProgressDialog = null
        super.onDestroy()
    }
}