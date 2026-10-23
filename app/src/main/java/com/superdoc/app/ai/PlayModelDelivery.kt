package com.superdoc.app.ai

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.android.play.core.ktx.requestFetch
import com.google.android.play.core.ktx.requestPackStates
import com.google.android.play.core.ktx.requestRemovePack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Models shipped inside the app's Play listing as asset packs. Google Play hosts and downloads them,
 * waits for Wi-Fi by itself, and checks their integrity, so the user only sees progress.
 * Outside Play (a debug build or a copied APK) packs are unavailable and [unavailable] says so.
 */
@Singleton
class PlayModelDelivery @Inject constructor(@ApplicationContext context: Context) {
    private val manager = AssetPackManagerFactory.getInstance(context)

    /** The model file if Play has delivered its pack, else null. */
    fun file(model: ModelInfo): File? {
        val pack = model.playPack ?: return null
        val assets = runCatching { manager.getPackLocation(pack)?.assetsPath() }.getOrNull() ?: return null
        return File(assets, model.file).takeIf { it.isFile }
    }

    fun listen(onUpdate: (AssetPackState) -> Unit) {
        manager.registerListener(AssetPackStateUpdateListener { onUpdate(it) })
    }

    suspend fun fetch(pack: String): AssetPackState? = manager.requestFetch(listOf(pack)).packStates()[pack]

    suspend fun state(pack: String): AssetPackState? = manager.requestPackStates(listOf(pack)).packStates()[pack]

    fun cancel(pack: String) {
        runCatching { manager.cancel(listOf(pack)) }
    }

    suspend fun remove(pack: String) = manager.requestRemovePack(pack)

    /** Shows Play's own dialog to download over mobile data, or to confirm a download Play is holding back. */
    fun askToContinue(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean =
        runCatching { manager.showConfirmationDialog(launcher) }.getOrDefault(false)

    companion object {
        fun toModelState(state: AssetPackState): ModelState = when (state.status()) {
            AssetPackStatus.PENDING -> ModelState.Downloading(0, state.totalBytesToDownload())
            AssetPackStatus.DOWNLOADING -> ModelState.Downloading(state.bytesDownloaded(), state.totalBytesToDownload())
            AssetPackStatus.TRANSFERRING, AssetPackStatus.COMPLETED -> ModelState.Verifying
            AssetPackStatus.WAITING_FOR_WIFI -> ModelState.Failed(FailReason.NOT_WIFI)
            AssetPackStatus.REQUIRES_USER_CONFIRMATION -> ModelState.Failed(FailReason.PLAY_CONFIRM)
            AssetPackStatus.FAILED -> ModelState.Failed(failReason(state.errorCode()))
            else -> ModelState.NotInstalled()
        }

        fun failReason(e: Throwable): FailReason =
            if (e is AssetPackException) failReason(e.errorCode) else FailReason.NETWORK

        private fun failReason(code: Int): FailReason = when (code) {
            AssetPackErrorCode.INSUFFICIENT_STORAGE -> FailReason.NO_SPACE
            AssetPackErrorCode.NETWORK_ERROR -> FailReason.NETWORK
            in unavailable -> FailReason.PLAY_UNAVAILABLE
            else -> FailReason.NETWORK
        }

        /** Errors meaning this install can't get packs from Play at all (not installed from Play, no Play services…). */
        private val unavailable = setOf(
            AssetPackErrorCode.APP_UNAVAILABLE,
            AssetPackErrorCode.PACK_UNAVAILABLE,
            AssetPackErrorCode.API_NOT_AVAILABLE,
            AssetPackErrorCode.ACCESS_DENIED,
            AssetPackErrorCode.APP_NOT_OWNED,
            AssetPackErrorCode.UNRECOGNIZED_INSTALLATION,
        )
    }
}
