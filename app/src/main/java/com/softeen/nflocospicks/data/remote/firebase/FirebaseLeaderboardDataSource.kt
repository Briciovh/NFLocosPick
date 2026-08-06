package com.softeen.nflocospicks.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseLeaderboardDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getLeaderboard(groupId: String): Flow<List<LeaderboardEntry>> = callbackFlow {
        val userCache = mutableMapOf<String, Pair<String, String?>>() // userId → (displayName, photoUrl)
        val scope: CoroutineScope = this

        val listener = firestore.collection("standings/$groupId/members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val standings = snapshot.documents.mapNotNull { doc ->
                    val totalPoints = (doc.getLong("totalPoints") ?: 0L).toInt()
                    @Suppress("UNCHECKED_CAST")
                    val rawBreakdown = doc.get("weeklyBreakdown") as? Map<String, Any> ?: emptyMap()
                    val weeklyBreakdown = rawBreakdown.mapValues { (_, v) ->
                        when (v) {
                            is Long -> v.toInt()
                            is Int  -> v
                            else    -> 0
                        }
                    }
                    Triple(doc.id, totalPoints, weeklyBreakdown)
                }

                scope.launch {
                    val uncached = standings.map { it.first }.filterNot { it in userCache }
                    uncached.forEach { uid ->
                        runCatching {
                            val userDoc = firestore.collection("users").document(uid).get().await()
                            // Falls back to username, then the raw uid, when displayName is blank —
                            // mirrors User.effectiveDisplayName, but this reads raw Firestore fields
                            // directly rather than going through the domain User object.
                            val name = userDoc.getString("displayName")?.takeIf { it.isNotBlank() }
                                ?: userDoc.getString("username")?.takeIf { it.isNotBlank() }
                                ?: uid
                            userCache[uid] = name to userDoc.getString("photoUrl")
                        }.onFailure {
                            userCache[uid] = uid to null
                        }
                    }

                    val entries = standings
                        .sortedByDescending { it.second }
                        .mapIndexed { index, (userId, totalPoints, weeklyBreakdown) ->
                            val (displayName, photoUrl) = userCache[userId] ?: (userId to null)
                            LeaderboardEntry(
                                userId          = userId,
                                displayName     = displayName,
                                photoUrl        = photoUrl,
                                totalPoints     = totalPoints,
                                weeklyBreakdown = weeklyBreakdown,
                                rank            = index + 1
                            )
                        }
                    trySend(entries)
                }
            }

        awaitClose { listener.remove() }
    }
}
