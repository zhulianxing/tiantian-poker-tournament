package com.pokernight.player

import android.app.Application
import com.pokernight.player.data.network.AuthManager

class PlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)
    }
}
