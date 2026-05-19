package com.example.famekodriver.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton client for Retrofit services
 */
object NetworkClient {
    // TOGGLE THIS: true = Local Backend, false = Production
    private const val USE_LOCAL_BACKEND = false

    private const val PRODUCTION_URL = "https://fameko-android-production.up.railway.app/"
    private const val PRODUCTION_ROUTING_URL = "https://fameko-routing-production.up.railway.app/"

    // 10.0.2.2 is ONLY for Emulators. 
    // YOUR PHONE is on 192.168.100.x
    // Set this to your COMPUTER'S IP (from 'ipconfig')
    private const val YOUR_COMPUTER_IP = "192.168.100.118"
    private const val LOCAL_URL = "http://$YOUR_COMPUTER_IP:8080/"
    private const val LOCAL_ROUTING_URL = "http://$YOUR_COMPUTER_IP:8012/"

    private val BASE_URL = if (USE_LOCAL_BACKEND) LOCAL_URL else PRODUCTION_URL
    private val ROUTING_URL = if (USE_LOCAL_BACKEND) LOCAL_ROUTING_URL else PRODUCTION_ROUTING_URL

    val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS) // 5 minutes for large image uploads
            .retryOnConnectionFailure(true)
            .build()
    }

    private val routingHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val routingRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ROUTING_URL)
            .client(routingHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val famekoApi: FamekoApiService by lazy {
        retrofit.create(FamekoApiService::class.java)
    }

    val routingApi: FamekoApiService by lazy {
        routingRetrofit.create(FamekoApiService::class.java)
    }

    val osmService: OpenStreetMapService by lazy {
        retrofit.create(OpenStreetMapService::class.java)
    }

    fun getWebSocketUrl(userId: String): String {
        val base = BASE_URL.removeSuffix("/")
        return base.replace("http", "ws") + "/ws/$userId"
    }

    // Keep for backward compatibility until all calls are migrated
    val geocodingService: FamekoApiService by lazy { famekoApi }
}