package com.pokernight.tvdisplay.network

import com.google.gson.Gson
import com.pokernight.tvdisplay.data.model.TableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RestApi(
    private val baseUrl: String = "http://43.164.130.145:80/poker"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun fetchTableState(tableCode: String): Result<TableState> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/v2/tables/$tableCode")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                Result.success(gson.fromJson(body, TableState::class.java))
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
