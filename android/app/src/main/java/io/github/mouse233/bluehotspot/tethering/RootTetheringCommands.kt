package io.github.mouse233.bluehotspot.tethering

import android.net.TetheringManager
import android.os.Parcelable
import android.os.Process
import be.mygod.librootkotlinx.RootCommand
import be.mygod.librootkotlinx.systemContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.parcelize.Parcelize
import java.util.concurrent.Executor
import kotlin.coroutines.resume

@Parcelize
data class RootTetheringResult(
    val errorCode: Int,
    val uid: Int,
) : Parcelable

/** Commands executed inside librootkotlinx's KernelSU-rooted app_process. */
object RootTetheringCommands {
    private val directExecutor = Executor { it.run() }
    private var activeRequest: TetheringManager.TetheringRequest? = null

    @Parcelize
    class Start : RootCommand<RootTetheringResult> {
        override suspend fun execute(): RootTetheringResult {
            val manager = systemContext.getSystemService(TetheringManager::class.java)
                ?: return RootTetheringResult(-1, Process.myUid())
            if (activeRequest != null) return RootTetheringResult(0, Process.myUid())

            val request = TetheringManager.TetheringRequest.Builder(
                TetheringManager.TETHERING_WIFI,
            ).build()

            return suspendCancellableCoroutine { continuation ->
                try {
                    manager.startTethering(
                        request,
                        directExecutor,
                        object : TetheringManager.StartTetheringCallback {
                            override fun onTetheringStarted() {
                                activeRequest = request
                                continuation.resume(RootTetheringResult(0, Process.myUid()))
                            }

                            override fun onTetheringFailed(error: Int) {
                                continuation.resume(RootTetheringResult(error, Process.myUid()))
                            }
                        },
                    )
                } catch (_: Throwable) {
                    continuation.resume(RootTetheringResult(-2, Process.myUid()))
                }
            }
        }
    }

    @Parcelize
    class Stop : RootCommand<RootTetheringResult> {
        override suspend fun execute(): RootTetheringResult {
            val manager = systemContext.getSystemService(TetheringManager::class.java)
                ?: return RootTetheringResult(-1, Process.myUid())
            val request = activeRequest ?: TetheringManager.TetheringRequest.Builder(
                TetheringManager.TETHERING_WIFI,
            ).build()

            return suspendCancellableCoroutine { continuation ->
                try {
                    manager.stopTethering(
                        request,
                        directExecutor,
                        object : TetheringManager.StopTetheringCallback {
                            override fun onStopTetheringSucceeded() {
                                activeRequest = null
                                continuation.resume(RootTetheringResult(0, Process.myUid()))
                            }

                            override fun onStopTetheringFailed(error: Int) {
                                continuation.resume(RootTetheringResult(error, Process.myUid()))
                            }
                        },
                    )
                } catch (_: Throwable) {
                    continuation.resume(RootTetheringResult(-2, Process.myUid()))
                }
            }
        }
    }
}

