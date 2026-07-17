package com.pokernight.player.data.network

import android.content.Context
import android.content.SharedPreferences

object AuthManager {
    private const val PREFS_NAME = "poker_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_PLAYER_ID = "player_id"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_PHONE = "phone"
    private const val KEY_AVATAR = "avatar"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun savePlayer(id: String, nickname: String, phone: String, avatar: String) {
        prefs.edit()
            .putString(KEY_PLAYER_ID, id)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_PHONE, phone)
            .putString(KEY_AVATAR, avatar)
            .apply()
    }

    fun getPlayerId(): String? = prefs.getString(KEY_PLAYER_ID, null)
    fun getNickname(): String? = prefs.getString(KEY_NICKNAME, null)
    fun getAvatar(): String? = prefs.getString(KEY_AVATAR, "🃏")

    fun isLoggedIn(): Boolean = getToken() != null

    fun logout() {
        prefs.edit().clear().apply()
    }
}
