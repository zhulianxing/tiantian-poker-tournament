package com.pokernight.player.data.network

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URISyntaxException

const val BASE_URL = "http://43.164.130.145"
const val SOCKET_URL = "http://43.164.130.145"

object NetworkProvider {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val api: RetrofitApi by lazy {
        Retrofit.Builder()
            .baseUrl("$BASE_URL/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetrofitApi::class.java)
    }

    fun createSocket(token: String?): Socket {
        val options = IO.Options().apply {
            transports = arrayOf(WebSocket.NAME)
            reconnection = true
            reconnectionAttempts = 5
            reconnectionDelay = 2000
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
