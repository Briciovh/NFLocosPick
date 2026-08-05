package com.softeen.nflocospicks

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.presentation.navigation.NavGraph
import com.softeen.nflocospicks.presentation.theme.NFLocosPickTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var prefsRepo: UserPreferencesRepository

    private var localeLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() debe llamarse antes de super.onCreate().
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !localeLoaded }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Lectura async del idioma guardado — la splash se mantiene visible
        // (sin bloquear el hilo principal) hasta que termine y se aplique.
        lifecycleScope.launch {
            applyStoredLocale()
            localeLoaded = true
        }

        setContent {
            NFLocosPickTheme {
                NavGraph()
            }
        }
    }

    private suspend fun applyStoredLocale() {
        val tag = prefsRepo.preferencesFlow.first().languageTag
        val localeList = if (tag.isNullOrEmpty()) LocaleListCompat.getEmptyLocaleList()
                         else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
