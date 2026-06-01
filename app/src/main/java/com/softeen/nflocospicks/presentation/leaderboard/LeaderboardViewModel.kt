package com.softeen.nflocospicks.presentation.leaderboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.model.MockSessionState
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetLeaderboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    savedStateHandle:                SavedStateHandle,
    private val getLeaderboardUseCase: GetLeaderboardUseCase,
    private val userRepository:        UserRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val mockSessionRepository: MockSessionRepository
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])
    val currentUserId: String? = userRepository.getCurrentUser()?.uid

    private val _uiState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) observeMockLeaderboard()
        else observeRealLeaderboard()
    }

    // ── Ruta real ─────────────────────────────────────────────────────────────

    private fun observeRealLeaderboard() {
        getLeaderboardUseCase(groupId)
            .onEach { entries -> _uiState.value = LeaderboardUiState.Success(entries) }
            .catch { e -> _uiState.value = LeaderboardUiState.Error(e.message ?: "Error al cargar el leaderboard") }
            .launchIn(viewModelScope)
    }

    // ── Ruta mock ─────────────────────────────────────────────────────────────

    private fun observeMockLeaderboard() {
        val realUser = userRepository.getCurrentUser()
        combine(
            preferencesRepository.preferencesFlow,
            mockSessionRepository.sessionFlow
        ) { prefs, session ->
            buildMockLeaderboard(
                simulating = prefs.simulateGamesStarted,
                session    = session,
                realUser   = realUser
            )
        }
            .onEach { entries -> _uiState.value = LeaderboardUiState.Success(entries) }
            .catch { e -> _uiState.value = LeaderboardUiState.Error(e.message ?: "Error al calcular el leaderboard") }
            .launchIn(viewModelScope)
    }

    private fun buildMockLeaderboard(
        simulating: Boolean,
        session:    MockSessionState,
        realUser:   User?
    ): List<LeaderboardEntry> {
        val games = MockDataProvider.MOCK_GAMES

        // (uid, displayName, photoUrl, points)
        data class Participant(val uid: String, val displayName: String, val photoUrl: String?, val points: Int)

        val participants = buildList {
            if (realUser != null) {
                add(Participant(
                    uid         = realUser.uid,
                    displayName = realUser.displayName,
                    photoUrl    = realUser.photoUrl,
                    points      = if (simulating)
                        countCorrectPicks(games, session.simulatedScores, session.realUserPicks)
                    else 0
                ))
            }
            MockDataProvider.MOCK_USERS.forEachIndexed { index, mockUser ->
                val picks = MockDataProvider.getPicksForMockUser(index, games)
                add(Participant(
                    uid         = mockUser.uid,
                    displayName = mockUser.displayName,
                    photoUrl    = mockUser.photoUrl,
                    points      = if (simulating)
                        countCorrectPicks(games, session.simulatedScores, picks)
                    else 0
                ))
            }
        }

        val sorted = participants.sortedByDescending { it.points }

        // Asignar rank — mismos puntos reciben el mismo rank.
        var currentRank = 1
        return sorted.mapIndexed { index, p ->
            if (index > 0 && sorted[index - 1].points > p.points) currentRank = index + 1
            LeaderboardEntry(
                userId          = p.uid,
                displayName     = p.displayName,
                photoUrl        = p.photoUrl,
                totalPoints     = p.points,
                weeklyBreakdown = if (simulating && p.points > 0)
                    mapOf(MockDataProvider.MOCK_WEEK_ID to p.points)
                else
                    emptyMap(),
                rank            = currentRank
            )
        }
    }

    // Cuenta picks correctos para un participante.
    // Empate → nadie recibe punto.
    private fun countCorrectPicks(
        games:  List<Game>,
        scores: Map<String, Pair<Int, Int>>,
        picks:  Map<String, String>
    ): Int = games.count { game ->
        val score = scores[game.id] ?: return@count false
        if (score.first == score.second) return@count false
        val winner = if (score.first > score.second) game.homeTeamAbbr else game.awayTeamAbbr
        picks[game.id] == winner
    }
}
