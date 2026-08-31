package com.example.bluehotspot

import android.app.Application
import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession

class BlueHotspotApplication : Application() {
    lateinit var rootSession: RootSession
        private set

    override fun onCreate() {
        super.onCreate()
        rootSession = object : RootSession() {
            override suspend fun initServer(server: RootServer) {
                server.init(this@BlueHotspotApplication)
            }
        }
    }
}
