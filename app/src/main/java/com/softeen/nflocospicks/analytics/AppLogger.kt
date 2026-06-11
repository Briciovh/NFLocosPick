package com.softeen.nflocospicks.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {
    fun logEvent(event: AppEvent) {
        Timber.i("[Analytics] ${event.name} params=${event.params}")
        firebaseAnalytics.logEvent(event.name, event.params.toBundle())
    }

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { b ->
        forEach { (k, v) ->
            when (v) {
                is String  -> b.putString(k, v)
                is Int     -> b.putInt(k, v)
                is Long    -> b.putLong(k, v)
                is Double  -> b.putDouble(k, v)
                is Boolean -> b.putBoolean(k, v)
                else       -> b.putString(k, v.toString())
            }
        }
    }
}
