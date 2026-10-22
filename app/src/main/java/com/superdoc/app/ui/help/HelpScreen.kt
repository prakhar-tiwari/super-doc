package com.superdoc.app.ui.help

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.help.HelpAssets
import com.superdoc.app.help.HelpAudio
import com.superdoc.app.help.HelpBox
import com.superdoc.app.help.HelpContent
import com.superdoc.app.help.HelpRepository
import com.superdoc.app.help.HelpTopic
import com.superdoc.app.share.ShareHelper
import com.superdoc.app.ui.LocalSettings
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BottomActions
import com.superdoc.app.ui.components.ButtonPair
import com.superdoc.app.ui.components.EmptyState
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.AppLocale
import com.superdoc.app.voice.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class HelpViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: HelpRepository,
    private val audio: HelpAudio,
    private val speaker: Speaker,
) : ViewModel() {
    val topicId: String = savedState.get<String>("topic") ?: "home"
    var topic by mutableStateOf<HelpTopic?>(null)
        private set
    var missing by mutableStateOf(false)
        private set
    var step by mutableIntStateOf(0)
        private set
    private var content: HelpContent? = null
    val audioPlaying = audio.playing
    val ttsSpeaking = speaker.speaking

    init {
        viewModelScope.launch {
            val c = repo.content()
            content = c
            topic = c.topic(topicId)
            missing = topic == null
        }
    }

    val isLast: Boolean get() = step == (topic?.steps?.size ?: 1) - 1

    fun text(hindi: Boolean): String = topic?.steps?.getOrNull(step)?.let { repo.text(it, hindi) }.orEmpty()

    fun box(hindi: Boolean): HelpBox? = content?.box(HelpRepository.lang(hindi), topicId, step)

    suspend fun image(hindi: Boolean): ImageBitmap? {
        val name = topic?.steps?.getOrNull(step)?.image ?: return null
        return repo.image(HelpRepository.lang(hindi), name)?.asImageBitmap()
    }

    fun next() {
        if (!isLast) step++
    }

    fun previous() {
        if (step > 0) step--
    }

    /** Plays the recorded voice for this step, or reads the text aloud if no recording is bundled. */
    fun play(hindi: Boolean) {
        stopVoice()
        val path = HelpAssets.audio(HelpRepository.lang(hindi), topicId, step)
        if (!repo.exists(path) || !audio.play(path)) speaker.speak(text(hindi))
    }

    fun stopVoice() {
        audio.stop()
        speaker.stop()
    }

    override fun onCleared() = stopVoice()
}

@Composable
fun HelpScreen(vm: HelpViewModel, onBack: () -> Unit, onHome: () -> Unit, onPractice: () -> Unit) {
    val hindi = AppLocale.isHindi()
    val topic = vm.topic
    val playing by vm.audioPlaying.collectAsState()
    val speaking by vm.ttsSpeaking.collectAsState()
    val voiceOn = playing || speaking

    LaunchedEffect(topic, vm.step) { if (topic != null) vm.play(hindi) }
    DisposableEffect(Unit) { onDispose { vm.stopVoice() } }

    SeniorScaffold(
        title = topic?.title?.get(hindi) ?: stringResource(R.string.help_title),
        onBack = onBack,
        onHome = onHome,
        bottomBar = {
            if (topic != null) {
                BottomActions {
                    if (topic.id == TOUR && vm.isLast) {
                        BigButton(stringResource(R.string.help_try_now), onPractice, icon = Icons.Filled.School, color = SeniorColors.SavedGreen)
                    }
                    BigOutlinedButton(
                        stringResource(if (voiceOn) R.string.help_stop_voice else R.string.help_listen),
                        { if (voiceOn) vm.stopVoice() else vm.play(hindi) },
                        icon = if (voiceOn) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                    )
                    ButtonPair(
                        first = { m ->
                            BigOutlinedButton(stringResource(R.string.help_previous), vm::previous, m, Icons.AutoMirrored.Filled.ArrowBack, enabled = vm.step > 0)
                        },
                        second = { m ->
                            if (vm.isLast) BigButton(stringResource(R.string.help_done), onBack, m, Icons.Filled.Check, SeniorColors.SavedGreen)
                            else BigButton(stringResource(R.string.help_next), vm::next, m, Icons.AutoMirrored.Filled.ArrowForward)
                        },
                    )
                }
            }
        },
    ) { padding ->
        when {
            vm.missing -> Box(Modifier.padding(padding)) { EmptyState(stringResource(R.string.help_missing)) }
            topic == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.help_step_of, vm.step + 1, topic.steps.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = SeniorColors.TextMuted,
                )
                Text(vm.text(hindi), style = MaterialTheme.typography.headlineSmall)
                val picture by produceState<ImageBitmap?>(null, vm.step, hindi) { value = vm.image(hindi) }
                picture?.let { HelpPicture(it, vm.box(hindi), stringResource(R.string.help_picture)) }
                FamilyCallButton()
            }
        }
    }
}

/** A tall phone screenshot is shown as a band around the highlight, so the marked button is large enough to see. */
@Composable
private fun HelpPicture(bitmap: ImageBitmap, box: HelpBox?, description: String) {
    val band = PICTURE_BAND
    val top = if (box == null) 0f else ((box.top + box.bottom) / 2 - band / 2).coerceIn(0f, 1f - band)
    val srcTop = (top * bitmap.height).roundToInt()
    val srcHeight = (band * bitmap.height).roundToInt().coerceAtMost(bitmap.height - srcTop)
    val shape = RoundedCornerShape(12.dp)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / srcHeight)
            .clip(shape)
            .border(2.dp, Color(0xFF9E9E9E), shape)
            .semantics { contentDescription = description },
    ) {
        val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
        drawImage(bitmap, srcOffset = IntOffset(0, srcTop), srcSize = IntSize(bitmap.width, srcHeight), dstSize = dst)
        if (box != null) {
            val scale = size.width / bitmap.width
            val pad = 6.dp.toPx()
            val rect = Rect(
                left = box.left * bitmap.width * scale - pad,
                top = (box.top * bitmap.height - srcTop) * scale - pad,
                right = box.right * bitmap.width * scale + pad,
                bottom = (box.bottom * bitmap.height - srcTop) * scale + pad,
            )
            val corner = CornerRadius(14.dp.toPx())
            val dim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(rect, corner))
            }
            drawPath(dim, Color.Black.copy(alpha = 0.45f))
            drawRoundRect(SeniorColors.Highlight, rect.topLeft, rect.size, corner, style = Stroke(5.dp.toPx()))
        }
    }
}

/** Shown on every help page: one tap to phone the family helper, or a hint on how to set one up. */
@Composable
fun FamilyCallButton() {
    val family = LocalSettings.current.family
    val context = LocalContext.current
    if (family != null) {
        BigOutlinedButton(
            stringResource(R.string.help_call_family, family.name),
            { ShareHelper.dial(context, family.phone) },
            icon = Icons.Filled.Phone,
            color = SeniorColors.WhatsApp,
        )
    } else {
        Text(stringResource(R.string.help_family_tip), style = MaterialTheme.typography.bodyMedium, color = SeniorColors.TextMuted)
    }
}

private const val TOUR = "tour"

/** Fraction of the screenshot's height shown around the highlight. */
private const val PICTURE_BAND = 0.42f
