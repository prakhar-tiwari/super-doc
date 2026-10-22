package com.superdoc.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.superdoc.app.data.SettingsRepository
import com.superdoc.app.ui.LocalSettings
import com.superdoc.app.ui.navigation.AppNavHost
import com.superdoc.app.ui.theme.SuperDocTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** AppCompatActivity (not ComponentActivity) so the per-app language also works on Android 8-12. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(BACKGROUND, BACKGROUND),
        )
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = null)
            settings?.let { loaded ->
                CompositionLocalProvider(LocalSettings provides loaded) {
                    SuperDocTheme(textScale = loaded.textScale) {
                        AppNavHost(startAtLanguage = !loaded.languageChosen)
                    }
                }
            }
        }
    }

    private companion object {
        const val BACKGROUND = 0xFFFFFBF5.toInt()
    }
}
