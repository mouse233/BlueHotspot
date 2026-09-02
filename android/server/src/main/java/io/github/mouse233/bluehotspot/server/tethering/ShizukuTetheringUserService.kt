package io.github.mouse233.bluehotspot.server.tethering

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.TetheringManager
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import androidx.annotation.Keep
import java.util.concurrent.Executor
import java.util.function.Supplier
import kotlin.system.exitProcess

/** Runs under Shizuku's ADB shell identity and owns the exact tethering request it starts. */
@Keep
class ShizukuTetheringUserService : IShizukuTetheringService.Stub {
    private var shellContext: Context? = null
    private var initializationError: String? = "Shizuku v13+ context is unavailable"
    private var activeRequest: TetheringManager.TetheringRequest? = null
    private val directExecutor = Executor { command -> command.run() }

    constructor()

    @Keep
    constructor(context: Context) {
        runCatching {
            shellContext = ShellOpPackageContext(context)
            initializationError = null
        }.onFailure { error ->
            initializationError = error.message ?: error.javaClass.simpleName
        }
    }

    override fun getUid(): Int = Process.myUid()

    override fun getOpPackageName(): String = shellContext?.opPackageName.orEmpty()

    override fun hasTetheringPermission(): Boolean {
        val context = shellContext ?: return false
        return context.checkPermission(
            TETHER_PRIVILEGED_PERMISSION,
            Process.myPid(),
            Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun start(callback: IShizukuTetheringResultCallback) {
        val manager = managerOrReport(callback) ?: return
        if (activeRequest != null) {
            callback.report(TetheringManager.TETHER_ERROR_NO_ERROR, "request already active")
            return
        }

        val request = TetheringManager.TetheringRequest.Builder(
            TetheringManager.TETHERING_WIFI,
        ).build()
        try {
            manager.startTethering(
                request,
                directExecutor,
                object : TetheringManager.StartTetheringCallback {
                    override fun onTetheringStarted() {
                        activeRequest = request
                        callback.report(TetheringManager.TETHER_ERROR_NO_ERROR, "started")
                    }

                    override fun onTetheringFailed(error: Int) {
                        callback.report(error, "start failed")
                    }
                },
            )
        } catch (error: Throwable) {
            callback.report(ERROR_EXCEPTION, error.describe())
        }
    }

    override fun stop(callback: IShizukuTetheringResultCallback) {
        val manager = managerOrReport(callback) ?: return
        val request = activeRequest
        if (request == null) {
            callback.report(TetheringManager.TETHER_ERROR_UNKNOWN_REQUEST, "no owned request")
            return
        }

        try {
            manager.stopTethering(
                request,
                directExecutor,
                object : TetheringManager.StopTetheringCallback {
                    override fun onStopTetheringSucceeded() {
                        activeRequest = null
                        callback.report(TetheringManager.TETHER_ERROR_NO_ERROR, "stopped")
                    }

                    override fun onStopTetheringFailed(error: Int) {
                        callback.report(error, "stop failed")
                    }
                },
            )
        } catch (error: Throwable) {
            callback.report(ERROR_EXCEPTION, error.describe())
        }
    }

    override fun destroy() {
        // Never stop by tethering type here: losing the request must fail closed.
        exitProcess(0)
    }

    private fun managerOrReport(
        callback: IShizukuTetheringResultCallback,
    ): TetheringManager? {
        val context = shellContext
        if (context == null) {
            callback.report(ERROR_INITIALIZATION, initializationError ?: "shell context unavailable")
            return null
        }
        if (Process.myUid() != SHELL_UID) {
            callback.report(ERROR_WRONG_UID, "expected shell uid $SHELL_UID")
            return null
        }
        if (!hasTetheringPermission()) {
            callback.report(ERROR_MISSING_PERMISSION, "shell lacks TETHER_PRIVILEGED")
            return null
        }
        return createTetheringManager(context).also { manager ->
            if (manager == null) callback.report(ERROR_INITIALIZATION, "TetheringManager unavailable")
        }
    }

    private fun createTetheringManager(context: Context): TetheringManager? {
        val binder = runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "tethering") as? IBinder
        }.getOrNull() ?: return null

        return runCatching {
            Class.forName("android.net.TetheringManager")
                .getConstructor(Context::class.java, Supplier::class.java)
                .newInstance(context, Supplier { binder }) as TetheringManager
        }.getOrNull()
    }

    private fun IShizukuTetheringResultCallback.report(errorCode: Int, detail: String) {
        try {
            onResult(errorCode, Process.myUid(), detail)
        } catch (_: RemoteException) {
            // The client disappeared; the owned request remains in this daemon service.
        }
    }

    private fun Throwable.describe(): String =
        "${javaClass.simpleName}: ${message ?: "no message"}"

    private class ShellOpPackageContext(base: Context) : ContextWrapper(base) {
        override fun getOpPackageName(): String = SHELL_PACKAGE
    }

    private companion object {
        const val SHELL_UID = 2000
        const val SHELL_PACKAGE = "com.android.shell"
        const val TETHER_PRIVILEGED_PERMISSION = "android.permission.TETHER_PRIVILEGED"
        const val ERROR_INITIALIZATION = -100
        const val ERROR_WRONG_UID = -101
        const val ERROR_WRONG_PACKAGE = -102
        const val ERROR_MISSING_PERMISSION = -103
        const val ERROR_EXCEPTION = -104
    }
}
