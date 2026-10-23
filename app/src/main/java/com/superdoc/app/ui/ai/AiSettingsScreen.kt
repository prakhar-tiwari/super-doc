package com.superdoc.app.ui.ai

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.ai.AiRunner
import com.superdoc.app.ai.DeviceCapability
import com.superdoc.app.ai.FailReason
import com.superdoc.app.ai.GenerationStats
import com.superdoc.app.ai.ModelCatalog
import com.superdoc.app.ai.ModelInfo
import com.superdoc.app.ai.ModelManager
import com.superdoc.app.ai.ModelState
import com.superdoc.app.data.SettingsRepository
import com.superdoc.app.scan.findActivity
import com.superdoc.app.ui.LocalSettings
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.components.ConfirmDialog
import com.superdoc.app.ui.components.MessageDialog
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class BenchmarkResult(val output: String, val stats: GenerationStats, val modelName: String)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    val device: DeviceCapability,
    val catalog: ModelCatalog,
    val models: ModelManager,
    private val runner: AiRunner,
    private val settings: SettingsRepository,
) : ViewModel() {
    val states = models.states

    var importing by mutableStateOf(false)
        private set
    var message by mutableStateOf<Int?>(null)
    var benchmarking by mutableStateOf(false)
        private set
    var benchmarkPartial by mutableStateOf("")
        private set
    var benchmark by mutableStateOf<BenchmarkResult?>(null)
        private set
    private var benchJob: Job? = null

    init {
        models.refresh()
    }

    val recommended: ModelInfo? get() = device.recommended(catalog.models)

    fun download(model: ModelInfo, allowMobileData: Boolean) = models.download(model, allowMobileData)

    fun pause(model: ModelInfo) = models.pause(model)

    fun delete(model: ModelInfo) = viewModelScope.launch { models.delete(model) }

    fun use(model: ModelInfo) = viewModelScope.launch { settings.setActiveModel(model.id) }

    fun import(uri: android.net.Uri) {
        importing = true
        viewModelScope.launch {
            val result = models.import(uri)
            importing = false
            message = if (result.isSuccess) R.string.ai_import_done else R.string.ai_import_unknown
        }
    }

    fun runBenchmark() {
        benchJob?.cancel()
        benchmark = null
        benchmarkPartial = ""
        benchmarking = true
        benchJob = viewModelScope.launch {
            try {
                val model = models.active()
                val (out, stats) = runner.benchmark(AppLocale.isHindi()) { p -> viewModelScope.launch { benchmarkPartial = p } }
                benchmark = BenchmarkResult(out, stats, model?.name.orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                message = R.string.ai_error_failed
            } finally {
                benchmarking = false
            }
        }
    }

    fun stopBenchmark() {
        benchJob?.cancel()
        benchmarking = false
    }
}

@Composable
fun AiSettingsScreen(vm: AiSettingsViewModel, onBack: () -> Unit, onHome: () -> Unit, onHelp: () -> Unit) {
    val states by vm.states.collectAsState()
    val activeId = LocalSettings.current.activeModelId
    var askMobileData by remember { mutableStateOf<ModelInfo?>(null) }
    var askDelete by remember { mutableStateOf<ModelInfo?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    val playDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val downloading = states.values.any { it is ModelState.Downloading || it is ModelState.Verifying }
    KeepScreenOn(downloading || vm.benchmarking)

    SeniorScaffold(title = stringResource(R.string.ai_settings_title), onBack = onBack, onHome = onHome, onHelp = onHelp) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.ai_privacy_long), style = MaterialTheme.typography.bodyLarge)
            PhoneCard(vm.device)
            if (!vm.device.canRunAi) return@Column

            vm.catalog.models.forEach { model ->
                ModelCard(
                    model = model,
                    state = states[model.id] ?: ModelState.NotInstalled(),
                    recommended = model == vm.recommended,
                    fits = vm.device.canRun(model),
                    active = model.id == activeId || (activeId == null && states[model.id] == ModelState.Installed),
                    onDownload = {
                        // Play asks about mobile data itself, with its own dialog.
                        if (model.playPack == null && vm.models.checkBeforeDownload(model, allowMobileData = false) == FailReason.NOT_WIFI) askMobileData = model
                        else vm.download(model, allowMobileData = false)
                    },
                    onAskPlay = { vm.models.askPlayToContinue(playDialog) },
                    onPause = { vm.pause(model) },
                    onUse = { vm.use(model) },
                    onDelete = { askDelete = model },
                )
            }

            Text(stringResource(R.string.ai_import_explain), style = MaterialTheme.typography.bodyMedium)
            BigOutlinedButton(stringResource(R.string.ai_import), { pickFile.launch(arrayOf("*/*")) }, icon = Icons.Filled.FileOpen)

            if (states.values.any { it == ModelState.Installed }) {
                Text(stringResource(R.string.ai_bench_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.ai_bench_explain), style = MaterialTheme.typography.bodyMedium)
                if (vm.benchmarking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (vm.benchmarkPartial.isNotBlank()) Text(vm.benchmarkPartial, style = MaterialTheme.typography.bodyLarge)
                    BigOutlinedButton(stringResource(R.string.ai_stop), vm::stopBenchmark, color = SeniorColors.Danger)
                } else {
                    BigButton(stringResource(R.string.ai_bench_run), vm::runBenchmark, icon = Icons.Filled.Speed, color = SeniorColors.Help)
                }
                vm.benchmark?.let { BenchmarkCard(it) }
            }
        }
    }

    askMobileData?.let { model ->
        ConfirmDialog(
            title = stringResource(R.string.ai_mobile_title),
            message = stringResource(R.string.ai_mobile_message, sizeLabel(model.sizeBytes)),
            confirmText = stringResource(R.string.ai_mobile_yes),
            dismissText = stringResource(R.string.ai_mobile_wait),
            onConfirm = { askMobileData = null; vm.download(model, allowMobileData = true) },
            onDismiss = { askMobileData = null },
        )
    }
    askDelete?.let { model ->
        ConfirmDialog(
            title = stringResource(R.string.ai_delete_title),
            message = stringResource(R.string.ai_delete_message, sizeLabel(model.sizeBytes)),
            confirmText = stringResource(R.string.ai_delete_yes),
            destructive = true,
            onConfirm = { askDelete = null; vm.delete(model) },
            onDismiss = { askDelete = null },
        )
    }
    vm.message?.let { MessageDialog(stringResource(R.string.ai_settings_title), stringResource(it), onDismiss = { vm.message = null }) }
    if (vm.importing) BusyOverlay(stringResource(R.string.ai_importing))
}

@Composable
private fun PhoneCard(device: DeviceCapability) {
    val gb = String.format(Locale.US, "%.1f", device.totalRamMb / 1024f)
    val ok = device.canRunAi
    Text(
        stringResource(if (ok) R.string.ai_phone_ok else R.string.ai_phone_too_small, gb),
        style = MaterialTheme.typography.titleMedium,
        color = if (ok) SeniorColors.SavedGreen else SeniorColors.Danger,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (ok) Color(0xFFE8F5E9) else Color(0xFFFFEBEE), RoundedCornerShape(12.dp))
            .padding(14.dp),
    )
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    state: ModelState,
    recommended: Boolean,
    fits: Boolean,
    active: Boolean,
    onDownload: () -> Unit,
    onAskPlay: () -> Unit,
    onPause: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (recommended) Modifier.border(3.dp, SeniorColors.Help, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ai_model_size, sizeLabel(model.sizeBytes)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(if (model.hindi) R.string.ai_languages_en_hi else R.string.ai_languages_en), style = MaterialTheme.typography.bodyMedium)
            if (!model.importOnly) Text(stringResource(R.string.ai_model_license, model.license), style = MaterialTheme.typography.bodySmall)
            when {
                recommended -> Badge(stringResource(R.string.ai_recommended), SeniorColors.Help)
                !fits -> Badge(stringResource(R.string.ai_needs_more_memory), SeniorColors.TextMuted)
            }
            when (state) {
                is ModelState.Installed -> {
                    Badge(stringResource(if (active) R.string.ai_in_use else R.string.ai_ready), SeniorColors.SavedGreen)
                    if (!active && fits) BigButton(stringResource(R.string.ai_use_this), onUse, icon = Icons.Filled.CheckCircle, color = SeniorColors.SavedGreen)
                    BigOutlinedButton(stringResource(R.string.ai_delete), onDelete, icon = Icons.Filled.Delete, color = SeniorColors.Danger)
                }
                is ModelState.Downloading -> {
                    val fraction = state.bytes.toFloat() / state.total
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.ai_downloading, (fraction * 100).toInt(), sizeLabel(state.bytes), sizeLabel(state.total)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(stringResource(R.string.ai_downloading_hint), style = MaterialTheme.typography.bodyMedium)
                    BigOutlinedButton(stringResource(R.string.ai_pause), onPause, icon = Icons.Filled.Pause)
                }
                ModelState.Verifying -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ai_verifying), style = MaterialTheme.typography.bodyLarge)
                }
                is ModelState.NotInstalled, is ModelState.Failed -> {
                    if (state is ModelState.Failed) {
                        Text(stringResource(failMessage(state.reason)), style = MaterialTheme.typography.bodyLarge, color = SeniorColors.Danger)
                    }
                    val partial = (state as? ModelState.NotInstalled)?.partialBytes ?: (state as ModelState.Failed).partialBytes
                    val playReason = (state as? ModelState.Failed)?.reason?.takeIf { model.playPack != null }
                    when {
                        model.importOnly -> Text(stringResource(R.string.ai_import_only, model.license), style = MaterialTheme.typography.bodyMedium)
                        !fits -> Unit
                        playReason == FailReason.NOT_WIFI ->
                            BigOutlinedButton(stringResource(R.string.ai_mobile_yes), onAskPlay, icon = Icons.Filled.Download)
                        playReason == FailReason.PLAY_CONFIRM ->
                            BigButton(stringResource(R.string.ai_play_continue), onAskPlay, icon = Icons.Filled.Download, color = SeniorColors.Help)
                        playReason == FailReason.PLAY_UNAVAILABLE && !model.directDownload -> Unit
                        partial > 0 -> BigButton(
                            stringResource(R.string.ai_continue_download, (partial * 100 / model.sizeBytes).toInt()),
                            onDownload, icon = Icons.Filled.Download, color = SeniorColors.Help,
                        )
                        else -> BigButton(stringResource(R.string.ai_download, sizeLabel(model.sizeBytes)), onDownload, icon = Icons.Filled.Download, color = SeniorColors.Help)
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun BenchmarkCard(result: BenchmarkResult) {
    val s = result.stats
    val verdict = when {
        s.tokensPerSecond >= 8f -> R.string.ai_bench_fast
        s.tokensPerSecond >= 3f -> R.string.ai_bench_ok
        else -> R.string.ai_bench_slow
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8E1), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(verdict), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                R.string.ai_bench_numbers,
                result.modelName,
                seconds(s.loadMs), seconds(s.firstWordMs), seconds(s.totalMs),
                String.format(Locale.US, "%.1f", s.tokensPerSecond),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.ai_bench_output), style = MaterialTheme.typography.labelLarge)
        Text(result.output, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun failMessage(reason: FailReason): Int = when (reason) {
    FailReason.NO_INTERNET -> R.string.ai_fail_no_internet
    FailReason.NOT_WIFI -> R.string.ai_fail_not_wifi
    FailReason.NO_SPACE -> R.string.ai_fail_no_space
    FailReason.NETWORK -> R.string.ai_fail_network
    FailReason.CHECKSUM -> R.string.ai_fail_checksum
    FailReason.IMPORT_UNKNOWN -> R.string.ai_import_unknown
    FailReason.PLAY_CONFIRM -> R.string.ai_fail_play_confirm
    FailReason.PLAY_UNAVAILABLE -> R.string.ai_fail_play_unavailable
}

private fun seconds(ms: Long) = String.format(Locale.US, "%.1f", ms / 1000f)

fun sizeLabel(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else String.format(Locale.US, "%.0f MB", mb)
}

/** Keeps the screen on during a long download, so the phone doesn't sleep and pause it. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
