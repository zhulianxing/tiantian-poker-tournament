package com.pokernight.player.data.network

import com.pokernight.player.data.model.AuthRequest
import com.pokernight.player.data.model.AuthResponse
import com.pokernight.player.data.model.GameHistory
import com.pokernight.player.data.model.TableInfo
import com.pokernight.player.data.model.TableStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface RetrofitApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body req: AuthRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body req: AuthRequest): AuthResponse

    @GET("api/v1/tables/{code}")
    suspend fun getTable(@Path("code") code: String): TableInfo

    @GET("api/v1/tables/{code}/status")
    suspend fun getTableStatus(@Path("code") code: String): TableStatus

    @POST("api/v1/tournaments/{id}/join")
    suspend fun joinTournament(
        @Path("id") id: String,
        @Header("Authorization") token: String,
    )

    @GET("api/v1/players/me/history")
    suspend fun getMyHistory(@Header("Authorization") token: String): List<GameHistory>
}
