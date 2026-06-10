package com.softeen.nflocospicks

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.presentation.navigation.NavGraph
import com.softeen.nflocospicks.presentation.theme.NFLocosPickTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var prefsRepo: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)  // Hilt inyecta aquí; prefsRepo disponible a partir de aquí
        applyStoredLocale()
        enableEdgeToEdge()
        setContent {
            NFLocosPickTheme {
                NavGraph()
            }
        }
    }

    private fun applyStoredLocale() {
        val tag = runBlocking { prefsRepo.preferencesFlow.first().languageTag }
        val localeList = if (tag.isNullOrEmpty()) LocaleListCompat.getEmptyLocaleList()
                         else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
