package com.example.bluehotspot

import android.app.Application
import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession
import com.example.bluehotspot.ble.BleGattServer
import com.example.bluehotspot.tethering.AndroidTetheringController
import com.example.bluehotspot.tethering.TetheringController

class BlueHotspotApplication : Application() {
    lateinit var rootSession: RootSession
        private set

    lateinit var tetheringController: TetheringController
        private set

    internal lateinit var bleGattServer: BleGattServer
        private set

    override fun onCreate() {
        super.onCreate()
        rootSession = object : RootSession() {
            override suspend fun initServer(server: RootServer) {
                server.init(this@BlueHotspotApplication)
            }
        }
        tetheringController = AndroidTetheringController(this)
        bleGattServer = BleGattServer(this, tetheringController)
    }
}

