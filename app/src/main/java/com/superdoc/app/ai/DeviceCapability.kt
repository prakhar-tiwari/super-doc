package com.superdoc.app.ai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What this phone can manage: enough memory for a model, free storage, and the kind of internet connection. */
@Singleton
class DeviceCapability @Inject constructor(@ApplicationContext private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /** Total RAM in MB. A "3 GB" phone reports roughly 2.7–2.9 GB here. */
    val totalRamMb: Int by lazy {
        val info = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        (info.totalMem / MB).toInt()
    }

    fun availableRamMb(): Int {
        val info = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        return (info.availMem / MB).toInt()
    }

    /**
     * False on phones that can't run any model; the AI buttons are then hidden. A 32-bit app
     * process cannot reliably map a 500 MB+ model, so those phones are left out too.
     */
    val canRunAi: Boolean get() = Process.is64Bit() && !activityManager.isLowRamDevice && totalRamMb >= MIN_RAM_MB

    fun canRun(model: ModelInfo): Boolean = canRunAi && totalRamMb >= model.minRamMb

    /** The best downloadable model that fits this phone. */
    fun recommended(models: List<ModelInfo>): ModelInfo? =
        models.filter { !it.importOnly && canRun(it) }.maxByOrNull { it.minRamMb }

    fun freeBytes(dir: File): Long = dir.usableSpace

    enum class Connection { NONE, UNMETERED, METERED }

    fun connection(): Connection {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return Connection.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return Connection.NONE
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) Connection.UNMETERED else Connection.METERED
    }

    companion object {
        const val MIN_RAM_MB = 2600
        private const val MB = 1024L * 1024L
    }
}
