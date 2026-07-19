package com.pokernight.player.data.network

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import com.google.gson.GsonBuilder
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URISyntaxException

const val BASE_URL = "https://poker.clawclaw.tech"
const val SOCKET_URL = "https://poker.clawclaw.tech"

object NetworkProvider {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val api: RetrofitApi by lazy {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        Retrofit.Builder()
            .baseUrl("$BASE_URL/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(RetrofitApi::class.java)
    }

    fun createSocket(token: String?): Socket {
        val options = IO.Options().apply {
            transports = arrayOf(WebSocket.NAME)
            reconnection = true
            reconnectionAttempts = 0 // 0 = unlimited; app must recover after doze/background
            reconnectionDelay = 1000
            reconnectionDelayMax = 10000
            randomizationFactor = 0.5
            if (token != null) {
                auth = mapOf("token" to token)
            }
        }
        return try {
            IO.socket(SOCKET_URL, options)
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }
    }
}
