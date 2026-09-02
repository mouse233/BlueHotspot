package io.github.mouse233.bluehotspot.server.tethering

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

internal class ShizukuTetheringBackend(
    context: Context,
) : TetheringBackend {
    override val name: String = "shizuku"

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, ShizukuTetheringUserService::class.java),
    )
        .daemon(true)
        .processNameSuffix("tethering")
        .tag("bluehotspot-tethering")
        .version(USER_SERVICE_VERSION)

    @Volatile
    private var service: IShizukuTetheringService? = null

    private var connectionResult: CompletableDeferred<IShizukuTetheringService>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val connected = IShizukuTetheringService.Stub.asInterface(binder)
            service = connected
            connectionResult?.complete(connected)
            connectionResult = null
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            connectionResult?.completeExceptionally(
                ShizukuUnavailableException("Shizuku UserService disconnected"),
            )
            connectionResult = null
        }
    }

    override suspend fun start(): TetheringBackendResult {
        val remote = prepareService(requestPermission = true)
        return invokeRemote(remote::start)
    }

    override suspend fun stop(): TetheringBackendResult {
        val remote = prepareService(requestPermission = true)
        return invokeRemote(remote::stop)
    }

    override suspend fun requestAuthorization(): TetheringBackendResult {
        awaitBinder()
        validateShizukuVersionAndUid()
        ensurePermission()
        return TetheringBackendResult(0, Shizuku.getUid(), "permission granted")
    }

    override suspend fun checkAvailability(): TetheringBackendResult {
        val remote = prepareService(requestPermission = false)
        return invokeRemote(remote::check)
    }

    private suspend fun prepareService(requestPermission: Boolean): IShizukuTetheringService {
        awaitBinder()
        validateShizukuVersionAndUid()
        if (requestPermission) ensurePermission() else ensurePermissionGranted()

        val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: bindService()
        if (remote.uid != SHELL_UID) {
            throw ShizukuUnavailableException("UserService has unexpected uid=${remote.uid}")
        }
        if (!remote.hasTetheringPermission()) {
            throw ShizukuUnavailableException("ADB shell lacks TETHER_PRIVILEGED on this device")
        }
        return remote
    }

    private fun validateShizukuVersionAndUid() {
        if (Shizuku.getVersion() < MIN_SHIZUKU_VERSION) {
            throw ShizukuUnavailableException("Shizuku v13 or newer is required")
        }
        if (Shizuku.getUid() != SHELL_UID) {
            throw ShizukuUnavailableException(
                "Shizuku must run through wireless debugging (uid=${Shizuku.getUid()})",
            )
        }
    }

    private suspend fun awaitBinder() {
        if (Shizuku.pingBinder()) return

        val received = CompletableDeferred<Unit>()
        val listener = Shizuku.OnBinderReceivedListener { received.complete(Unit) }
        Shizuku.addBinderReceivedListenerSticky(listener)
        try {
            withTimeout(BINDER_TIMEOUT_MILLIS) { received.await() }
        } catch (error: Throwable) {
            throw ShizukuUnavailableException("Shizuku is not running", error)
        } finally {
            Shizuku.removeBinderReceivedListener(listener)
        }
    }

    private suspend fun ensurePermission() {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            throw ShizukuUnavailableException("Shizuku permission was denied")
        }

        val permissionResult = CompletableDeferred<Int>()
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == PERMISSION_REQUEST_CODE) permissionResult.complete(grantResult)
        }
        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            val result = withTimeout(PERMISSION_TIMEOUT_MILLIS) { permissionResult.await() }
            if (result != PackageManager.PERMISSION_GRANTED) {
                throw ShizukuUnavailableException("Shizuku permission was denied")
            }
        } finally {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
    }

    private fun ensurePermissionGranted() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw ShizukuUnavailableException("Shizuku permission is not granted")
        }
    }

    private suspend fun bindService(): IShizukuTetheringService {
        connectionResult?.let { return withTimeout(BINDER_TIMEOUT_MILLIS) { it.await() } }

        val pending = CompletableDeferred<IShizukuTetheringService>()
        connectionResult = pending
        try {
            Shizuku.bindUserService(serviceArgs, connection)
            return withTimeout(BINDER_TIMEOUT_MILLIS) { pending.await() }
        } catch (error: Throwable) {
            connectionResult = null
            throw ShizukuUnavailableException("Unable to bind Shizuku UserService", error)
        }
    }

    private suspend fun invokeRemote(
        operation: (IShizukuTetheringResultCallback) -> Unit,
    ): TetheringBackendResult {
        val result = CompletableDeferred<TetheringBackendResult>()
        val callback = object : IShizukuTetheringResultCallback.Stub() {
            override fun onResult(errorCode: Int, uid: Int, detail: String) {
                result.complete(TetheringBackendResult(errorCode, uid, detail))
            }
        }
        try {
            operation(callback)
            return withTimeout(OPERATION_TIMEOUT_MILLIS) { result.await() }
        } catch (error: Throwable) {
            service = null
            return TetheringBackendResult(
                TetheringBackendResult.ERROR_OPERATION_UNCERTAIN,
                SHELL_UID,
                "operation result uncertain: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    internal class ShizukuUnavailableException(
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    private companion object {
        const val SHELL_UID = 2000
        const val MIN_SHIZUKU_VERSION = 13
        const val USER_SERVICE_VERSION = 1
        const val PERMISSION_REQUEST_CODE = 0x4248
        const val BINDER_TIMEOUT_MILLIS = 3_000L
        const val PERMISSION_TIMEOUT_MILLIS = 60_000L
        const val OPERATION_TIMEOUT_MILLIS = 30_000L
    }
}
