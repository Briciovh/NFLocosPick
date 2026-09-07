package com.softeen.nflocospicks.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [DataStore] of [Preferences] for JVM unit tests — no file IO, no
 * Android runtime. `androidx.datastore.preferences.core.edit { }` works on top of
 * this because it only calls [updateData].
 *
 * [updateData] is serialized with a [Mutex] to mirror the real DataStore's
 * atomic read-transform-write guarantee, so concurrent updates don't interleave.
 */
class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    private val writeLock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = writeLock.withLock {
        val updated = transform(state.value)
        state.value = updated
        updated
    }
}
