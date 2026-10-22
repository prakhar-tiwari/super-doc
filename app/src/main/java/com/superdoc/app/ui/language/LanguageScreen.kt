package com.superdoc.app.ui.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.data.SettingsRepository
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun choose(tag: String, onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setLanguageChosen()
            onDone()
            AppLocale.set(tag)
        }
    }
}

/** First screen ever shown. Both labels are written in their own language so either reader understands. */
@Composable
fun LanguageScreen(onChoose: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Translate, contentDescription = null, tint = SeniorColors.Primary, modifier = Modifier.size(72.dp))
        Text(
            "अपनी भाषा चुनें\nChoose your language",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        LanguageCard("हिन्दी", "मुझे हिन्दी में चाहिए", SeniorColors.Note) { onChoose(AppLocale.HINDI) }
        LanguageCard("English", "I want English", SeniorColors.Documents) { onChoose(AppLocale.ENGLISH) }
        Text(
            "आप इसे बाद में सेटिंग में बदल सकते हैं।\nYou can change this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LanguageCard(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = Color.White),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 40.sp, style = MaterialTheme.typography.displaySmall)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
