package com.superdoc.app.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.SettingsRepository
import com.superdoc.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

sealed interface ModelState {
    /** [partialBytes] > 0 means a paused download that can continue where it stopped. */
    data class NotInstalled(val partialBytes: Long = 0) : ModelState
    data class Downloading(val bytes: Long, val total: Long) : ModelState
    data object Verifying : ModelState
    data object Installed : ModelState
    data class Failed(val reason: FailReason, val partialBytes: Long = 0) : ModelState
}

enum class FailReason { NO_INTERNET, NOT_WIFI, NO_SPACE, NETWORK, CHECKSUM, IMPORT_UNKNOWN, PLAY_CONFIRM, PLAY_UNAVAILABLE }

/**
 * Gets, checks and stores AI models. Models in a Play asset pack come from Google Play; others are
 * downloaded into app-private storage and only accepted if their SHA-256 matches the catalog.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: FileStore,
    private val catalog: ModelCatalog,
    private val device: DeviceCapability,
    private val settings: SettingsRepository,
    private val play: PlayModelDelivery,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    init {
        refresh()
        play.listen { packState ->
            catalog.models.filter { it.playPack == packState.name() }.forEach { onPlayUpdate(it, PlayModelDelivery.toModelState(packState)) }
        }
        scope.launch { restorePlayDownloads(); autoSetup() }
    }

    /** The pack Play delivered, or else a file downloaded or imported into app storage. */
    fun fileFor(model: ModelInfo) = play.file(model) ?: localFileFor(model)
    private fun localFileFor(model: ModelInfo) = File(files.modelsDir, model.file)
    private fun partFor(model: ModelInfo) = File(files.modelsDir, model.file + ".part")

    fun refresh() {
        _states.value = catalog.models.associate { m ->
            val current = _states.value[m.id]
            val playBusy = m.playPack != null && (current is ModelState.Downloading || current == ModelState.Verifying)
            m.id to when {
                jobs[m.id]?.isActive == true || playBusy -> current ?: ModelState.NotInstalled()
                fileFor(m).exists() -> ModelState.Installed
                m.playPack != null && current is ModelState.Failed -> current
                else -> ModelState.NotInstalled(partFor(m).takeIf { it.exists() }?.length() ?: 0)
            }
        }
    }

    private fun onPlayUpdate(model: ModelInfo, state: ModelState) {
        if (state == ModelState.Verifying && fileFor(model).exists()) {
            set(model, ModelState.Installed)
            scope.launch { if (settings.current().activeModelId == null) settings.setActiveModel(model.id) }
        } else if (!(state is ModelState.NotInstalled && fileFor(model).exists())) {
            set(model, state)
        }
    }

    /** Picks up a Play download that was already running before the app was (re)opened. */
    private suspend fun restorePlayDownloads() {
        catalog.models.filter { it.playPack != null && !fileFor(it).exists() }.forEach { m ->
            runCatching { play.state(m.playPack!!) }.getOrNull()?.let { onPlayUpdate(m, PlayModelDelivery.toModelState(it)) }
        }
    }

    /**
     * On first start, asks Play for the recommended helper so nobody has to set it up. Play waits for
     * Wi-Fi before downloading anything this large, and phones that can't run it never ask.
     */
    private suspend fun autoSetup() {
        if (settings.current().aiAutoSetupDone) return
        settings.setAiAutoSetupDone()
        if (installed().isNotEmpty()) return
        val model = device.recommended(catalog.models)?.takeIf { it.playPack != null } ?: return
        download(model, allowMobileData = false)
    }

    private fun set(model: ModelInfo, state: ModelState) = _states.update { it + (model.id to state) }

    fun installed(): List<ModelInfo> = catalog.models.filter { fileFor(it).exists() }

    /** The model to use: the one chosen in Settings if it is installed, otherwise any installed one that fits. */
    suspend fun active(): ModelInfo? {
        val chosen = catalog.find(settings.current().activeModelId)
        val usable = installed().filter { device.canRun(it) }
        return chosen?.takeIf { it in usable } ?: usable.firstOrNull()
    }

    /** Checks the connection and space; returns a reason if the download should not start. */
    fun checkBeforeDownload(model: ModelInfo, allowMobileData: Boolean): FailReason? {
        when (device.connection()) {
            DeviceCapability.Connection.NONE -> return FailReason.NO_INTERNET
            DeviceCapability.Connection.METERED -> if (!allowMobileData) return FailReason.NOT_WIFI
            DeviceCapability.Connection.UNMETERED -> Unit
        }
        val remaining = model.sizeBytes - (partFor(model).takeIf { it.exists() }?.length() ?: 0)
        if (device.freeBytes(files.modelsDir) < remaining + SPACE_MARGIN) return FailReason.NO_SPACE
        return null
    }

    fun download(model: ModelInfo, allowMobileData: Boolean) {
        if (jobs[model.id]?.isActive == true) return
        val pack = model.playPack
        if (pack != null) {
            jobs[model.id] = scope.launch {
                set(model, ModelState.Downloading(0, model.sizeBytes))
                try {
                    play.fetch(pack)?.let { onPlayUpdate(model, PlayModelDelivery.toModelState(it)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val reason = PlayModelDelivery.failReason(e)
                    Log.w(TAG, "Play could not deliver ${model.id}: $e")
                    if (reason == FailReason.PLAY_UNAVAILABLE && model.directDownload) {
                        set(model, ModelState.NotInstalled())
                        jobs.remove(model.id)
                        downloadDirect(model, allowMobileData)
                    } else {
                        set(model, ModelState.Failed(reason))
                    }
                }
            }
            return
        }
        downloadDirect(model, allowMobileData)
    }

    private fun downloadDirect(model: ModelInfo, allowMobileData: Boolean) {
        if (!model.directDownload) return
        checkBeforeDownload(model, allowMobileData)?.let { set(model, ModelState.Failed(it, partFor(model).length())); return }
        jobs[model.id] = scope.launch(Dispatchers.IO) {
            try {
                runDownload(model)
                if (settings.current().activeModelId == null) settings.setActiveModel(model.id)
                set(model, ModelState.Installed)
            } catch (e: CancellationException) {
                set(model, ModelState.NotInstalled(partFor(model).length()))
                throw e
            } catch (e: ChecksumException) {
                Log.w(TAG, "Checksum mismatch for ${model.id}")
                set(model, ModelState.Failed(FailReason.CHECKSUM))
            } catch (e: IOException) {
                Log.w(TAG, "Download of ${model.id} stopped: $e")
                set(model, ModelState.Failed(FailReason.NETWORK, partFor(model).length()))
            }
        }
    }

    /** Stops a download; what was downloaded is kept so it can continue later. */
    fun pause(model: ModelInfo) {
        jobs.remove(model.id)?.cancel()
        model.playPack?.let { play.cancel(it); set(model, ModelState.NotInstalled()) }
    }

    /** For a Play download that is waiting for Wi-Fi or for the user's OK: shows Play's own dialog. */
    fun askPlayToContinue(launcher: ActivityResultLauncher<IntentSenderRequest>) = play.askToContinue(launcher)

    suspend fun delete(model: ModelInfo) {
        pause(model)
        if (play.file(model) != null) runCatching { play.remove(model.playPack!!) }
        withContext(Dispatchers.IO) {
            localFileFor(model).delete()
            partFor(model).delete()
        }
        if (settings.current().activeModelId == model.id) settings.setActiveModel(installed().firstOrNull()?.id)
        set(model, ModelState.NotInstalled())
    }

    private suspend fun runDownload(model: ModelInfo) {
        val expectedSha = requireNotNull(model.sha256)
        val url = URL(requireNotNull(model.url))
        require(url.protocol == "https" && url.host in ALLOWED_HOSTS) { "Untrusted model address" }
        val part = partFor(model)
        val digest = MessageDigest.getInstance("SHA-256")
        var have = if (part.exists()) part.length() else 0L
        if (have > model.sizeBytes) { part.delete(); have = 0 }
        if (have > 0) part.inputStream().use { feed(it, digest) }

        val connection = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = connection.responseCode
            // Redirects may only go to other HTTPS addresses.
            if (connection.url.protocol != "https") throw IOException("Insecure redirect")
            when {
                code == HttpsURLConnection.HTTP_PARTIAL -> Unit
                code == HttpsURLConnection.HTTP_OK -> if (have > 0) { have = 0; digest.reset(); part.delete() }
                code == HTTP_RANGE_NOT_SATISFIABLE && have == model.sizeBytes -> Unit
                else -> throw IOException("HTTP $code")
            }
            if (code != HTTP_RANGE_NOT_SATISFIABLE) {
                connection.inputStream.use { input ->
                    FileOutputStream(part, have > 0).use { out ->
                        val buffer = ByteArray(BUFFER)
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            have += n
                            if (have - lastReport >= REPORT_EVERY || have == model.sizeBytes) {
                                lastReport = have
                                set(model, ModelState.Downloading(have, model.sizeBytes))
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (have != model.sizeBytes) throw IOException("Incomplete download")
        set(model, ModelState.Verifying)
        if (digest.hex() != expectedSha) {
            part.delete()
            throw ChecksumException()
        }
        if (!part.renameTo(localFileFor(model))) throw IOException("Could not store model")
    }

    /**
     * Copies a model file the user picked (for models whose licence must be accepted on the web first).
     * The file is matched to the catalog by checksum, or else by its name ("gemma", "qwen").
     */
    suspend fun import(uri: Uri): Result<ModelInfo> = withContext(Dispatchers.IO) {
        val name = displayName(uri).orEmpty()
        val temp = File(files.modelsDir, "import.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
            input.use { src -> FileOutputStream(temp).use { out -> copyHashing(src, out, digest) } }
            val sha = digest.hex()
            val model = catalog.models.firstOrNull { it.sha256 == sha }
                ?: guessByName(name)
                ?: return@withContext Result.failure(UnknownModelException())
            temp.renameTo(localFileFor(model)).let { if (!it) throw IOException("Could not store model") }
            if (settings.current().activeModelId == null) settings.setActiveModel(model.id)
            set(model, ModelState.Installed)
            Result.success(model)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            temp.delete()
        }
    }

    private fun guessByName(name: String): ModelInfo? {
        val lower = name.lowercase()
        if (!lower.endsWith(".task")) return null
        val template = when {
            "gemma" in lower -> ChatTemplate.GEMMA
            "qwen" in lower -> ChatTemplate.QWEN
            else -> return null
        }
        val sameTemplate = catalog.models.filter { it.template == template }
        return sameTemplate.firstOrNull { it.importOnly } ?: sameTemplate.minByOrNull { it.minRamMb }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private suspend fun copyHashing(input: InputStream, out: FileOutputStream, digest: MessageDigest) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            digest.update(buffer, 0, n)
        }
    }

    private fun feed(input: InputStream, digest: MessageDigest) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }

    class ChecksumException : IOException("Checksum mismatch")
    class UnknownModelException : IOException("Unknown model file")

    companion object {
        private const val TAG = "ModelManager"
        private val ALLOWED_HOSTS = setOf("huggingface.co")
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER = 256 * 1024
        private const val REPORT_EVERY = 2L * 1024 * 1024
        private const val SPACE_MARGIN = 200L * 1024 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}

internal fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }
