package com.softeen.nflocospicks

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.softeen.nflocospicks.domain.model.AuthException
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.presentation.auth.messageRes
import com.softeen.nflocospicks.presentation.navigation.NavGraph
import com.softeen.nflocospicks.presentation.theme.NFLocosPickTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var prefsRepo: UserPreferencesRepository
    @Inject lateinit var userRepository: UserRepository

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

        handleEmailLinkIntent(intent)

        setContent {
            NFLocosPickTheme {
                NavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEmailLinkIntent(intent)
    }

    private fun handleEmailLinkIntent(intent: Intent?) {
        val link = intent?.data?.toString() ?: return
        if (!userRepository.isSignInWithEmailLink(link)) return

        val pendingEmail = userRepository.getPendingSignInEmail()
        if (pendingEmail == null) {
            Toast.makeText(this, getString(R.string.error_email_link_no_pending), Toast.LENGTH_LONG).show()
            return
        }

        // A session is already active (e.g. a phone-signed-in user opening the link they just
        // sent themselves from the Account screen to add an email) — link the credential onto
        // the current account instead of starting a brand-new sign-in.
        val isLinkingToExistingSession = userRepository.getCurrentUser() != null

        lifecycleScope.launch {
            val outcome = if (isLinkingToExistingSession) {
                userRepository.linkEmailCredential(pendingEmail, link)
            } else {
                userRepository.signInWithEmailLink(pendingEmail, link)
            }
            outcome.onFailure {
                val messageRes = (it as? AuthException)?.error?.messageRes()
                    ?: R.string.error_email_link_sign_in_failed
                Toast.makeText(this@MainActivity, getString(messageRes), Toast.LENGTH_LONG).show()
            }
            // No success handling needed: both paths update FirebaseAuth's current user (or
            // its linked providers/Firestore profile), which UserRepositoryImpl's
            // addAuthStateListener / watchCurrentUser already propagate reactively.
        }
    }

    private suspend fun applyStoredLocale() {
        val tag = prefsRepo.preferencesFlow.first().languageTag
        val localeList = if (tag.isNullOrEmpty()) LocaleListCompat.getEmptyLocaleList()
                         else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
