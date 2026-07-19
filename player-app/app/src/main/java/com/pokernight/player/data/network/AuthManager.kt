package com.pokernight.player.data.network

import android.content.Context
import android.content.SharedPreferences

object AuthManager {
    private const val PREFS_NAME = "poker_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_PLAYER_ID = "player_id"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_EMAIL = "email"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_ACTIVE_TABLE = "active_table"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun savePlayer(id: String, nickname: String, email: String, avatar: String) {
        prefs.edit()
            .putString(KEY_PLAYER_ID, id)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_EMAIL, email)
            .putString(KEY_AVATAR, avatar)
            .apply()
    }

    fun getPlayerId(): String? = prefs.getString(KEY_PLAYER_ID, null)
    fun getNickname(): String? = prefs.getString(KEY_NICKNAME, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getAvatar(): String? = prefs.getString(KEY_AVATAR, "🃏")

    fun isLoggedIn(): Boolean = getToken() != null

    // ─── 活跃牌桌（用于"返回牌局"入口） ───

    fun saveActiveTable(tableCode: String) {
        prefs.edit().putString(KEY_ACTIVE_TABLE, tableCode).apply()
    }

    fun getActiveTable(): String? = prefs.getString(KEY_ACTIVE_TABLE, null)

    fun clearActiveTable() {
        prefs.edit().remove(KEY_ACTIVE_TABLE).apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
