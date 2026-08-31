package io.github.mouse233.bluehotspot.server

import android.app.Application
import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession
import io.github.mouse233.bluehotspot.server.ble.BleGattServer
import io.github.mouse233.bluehotspot.server.tethering.AndroidTetheringController
import io.github.mouse233.bluehotspot.server.tethering.TetheringController

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


