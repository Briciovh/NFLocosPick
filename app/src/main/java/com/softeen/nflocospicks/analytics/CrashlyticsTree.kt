package com.softeen.nflocospicks.analytics

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

class CrashlyticsTree : Timber.Tree() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return
        val formatted = if (tag != null) "[$tag] $message" else message
        crashlytics.log(formatted)
        t?.let { crashlytics.recordException(it) }
    }
}
