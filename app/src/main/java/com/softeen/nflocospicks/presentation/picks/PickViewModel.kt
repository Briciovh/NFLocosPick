package com.softeen.nflocospicks.presentation.picks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.NflSeasonCalendar
import com.softeen.nflocospicks.domain.model.SeasonType
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import com.softeen.nflocospicks.domain.usecase.GetGamesForWeekUseCase
import com.softeen.nflocospicks.domain.usecase.GetWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.SubmitPickUseCase
import com.softeen.nflocospicks.presentation.theme.IconScaleOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

@HiltViewModel
class PickViewModel @Inject constructor(
    private val getCurrentWeekGamesUseCase: GetCurrentWeekGamesUseCase,
    private val getGamesForWeekUseCase:     GetGamesForWeekUseCase,
    private val getWeekPicksUseCase:        GetWeekPicksUseCase,
    private val submitPickUseCase:          SubmitPickUseCase,
    private val scoreWeekPicksUseCase:      ScoreWeekPicksUseCase,
    private val userRepository:             UserRepository,
    private val preferencesRepository:      UserPreferencesRepository,
    private val mockSessionRepository:      MockSessionRepository,
    private val logger:                     AppLogger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])

    val weekTabsVisible: Boolean = groupId != MockDataProvider.MOCK_GROUP_ID

    private val _uiState = MutableStateFlow<PickUiState>(PickUiState.Loading)
    val uiState: StateFlow<PickUiState> = _uiState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedWeekIndex = MutableStateFlow(NflSeasonCalendar.DEFAULT_INDEX)
    val selectedWeekIndex: StateFlow<Int> = _selectedWeekIndex.asStateFlow()

    // Semana que ESPN reporta como "la actual" — null hasta resolverla. Se usa
    // para saber si el botón de sync (que solo puntúa la semana actual en el
    // Cloud Function) tiene sentido en el tab que se está viendo.
    private val _currentWeekIndex = MutableStateFlow<Int?>(null)
    val currentWeekIndex: StateFlow<Int?> = _currentWeekIndex.asStateFlow()

    val iconScale: StateFlow<IconScaleOption> = preferencesRepository.preferencesFlow
        .map { IconScaleOption.fromKey(it.iconScalePreference) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IconScaleOption.GRANDE)

    // Cache en memoria por índice de tab — evitar re-pedir a ESPN cada vez que
    // el usuario va y viene entre semanas ya visitadas en esta sesión.
    private val weekCache = mutableMapOf<Int, PickUiState.Success>()

    init {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) observeMockPicks()
        else {
            resolveCurrentWeekAndLoad()
            startAutoRefresh()
        }
    }

    // ── Ruta real ─────────────────────────────────────────────────────────────

    // Resuelve qué tab es "la semana actual" (vía el rango de fechas de
    // getCurrentWeekGamesUseCase) y la muestra. En temporada muerta ESPN no
    // devuelve juegos "actuales" — cae al tab por defecto (Semana 1 regular).
    private fun resolveCurrentWeekAndLoad() {
        val userId = userRepository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            _uiState.value = PickUiState.Loading
            try {
                val games = getCurrentWeekGamesUseCase(groupId)
                val first = games.firstOrNull()
                val resolvedIndex = first?.let { NflSeasonCalendar.indexOf(it.seasonType, it.weekNumber) } ?: -1
                if (resolvedIndex >= 0) {
                    _currentWeekIndex.value = resolvedIndex
                    _selectedWeekIndex.value = resolvedIndex
                    publish(resolvedIndex, buildSuccess(games, userId))
                } else {
                    _selectedWeekIndex.value = NflSeasonCalendar.DEFAULT_INDEX
                    loadWeek(NflSeasonCalendar.DEFAULT_INDEX, showLoading = true)
                }
            } catch (e: Exception) {
                _uiState.value = PickUiState.Error(e.message ?: "Error al cargar los partidos")
            }
        }
    }

    // Retry (botón en el estado de error): reintenta resolver la semana
    // actual si nunca se resolvió, o recarga la semana seleccionada.
    fun loadData() {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) return
        if (_currentWeekIndex.value == null) resolveCurrentWeekAndLoad()
        else loadWeek(_selectedWeekIndex.value, showLoading = true)
    }

    fun onWeekSelected(index: Int) {
        if (groupId == MockDataProvider.MOCK_GROUP_ID || index == _selectedWeekIndex.value) return
        _selectedWeekIndex.value = index
        val cached = weekCache[index]
        // Una vez cacheada, una semana se confía tal cual al volver a ella — no
        // se revalida en silencio. Revalidar acá pisaría un pick optimista que
        // el usuario acaba de hacer con lo que getWeekPicksUseCase todavía lee
        // de Firestore (que puede no reflejar la escritura recién hecha). El
        // auto-refresh y el pull-to-refresh siguen refrescando la semana
        // visible con el tiempo.
        if (cached != null) _uiState.value = cached
        else loadWeek(index, showLoading = true)
    }

    // Pull-to-refresh y auto-refresh: re-consulta la semana SELECCIONADA sin
    // ocultar lo que ya se está mostrando.
    fun refresh() {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) return
        if (_uiState.value !is PickUiState.Success) return
        loadWeek(_selectedWeekIndex.value, showLoading = false)
    }

    private fun loadWeek(index: Int, showLoading: Boolean) {
        val userId = userRepository.getCurrentUser()?.uid ?: return
        val week = NflSeasonCalendar.WEEKS[index]
        viewModelScope.launch {
            if (showLoading) {
                if (_selectedWeekIndex.value == index) _uiState.value = PickUiState.Loading
            } else {
                markRefreshing(index, true)
            }
            try {
                val games = getGamesForWeekUseCase(week.seasonType, week.weekNumber)
                publish(index, buildSuccess(games, userId))
            } catch (e: Exception) {
                if (showLoading) {
                    if (_selectedWeekIndex.value == index) {
                        _uiState.value = PickUiState.Error(e.message ?: "Error al cargar los partidos")
                    }
                } else {
                    Timber.w(e, "No se pudo refrescar la semana (no fatal)")
                    markRefreshing(index, false)
                }
            }
        }
    }

    private suspend fun buildSuccess(games: List<Game>, userId: String): PickUiState.Success {
        val weekId = games.firstOrNull()?.weekId ?: ""
        val isPreseason = games.firstOrNull()?.seasonType == SeasonType.PRESEASON
        val picks = if (weekId.isNotEmpty()) getWeekPicksUseCase(groupId, weekId, userId) else emptyMap()
        val now = System.currentTimeMillis()
        val items = games.sortedBy { it.kickoffTime }.map { game ->
            GamePickItem(
                game       = game,
                pickedTeam = picks[game.id]?.pickedTeam,
                isLocked   = now >= game.kickoffTime || game.status != GameStatus.SCHEDULED
            )
        }
        return PickUiState.Success(items, weekId, isPreseason)
    }

    // Escribe en la cache y, si [index] sigue siendo el tab seleccionado,
    // también actualiza lo que se ve — evita que una respuesta tardía de un
    // tab que el usuario ya abandonó pise la vista actual.
    private fun publish(index: Int, state: PickUiState.Success) {
        weekCache[index] = state
        if (_selectedWeekIndex.value == index) _uiState.value = state
    }

    private fun markRefreshing(index: Int, refreshing: Boolean) {
        val base = weekCache[index]
            ?: (_uiState.value as? PickUiState.Success)?.takeIf { _selectedWeekIndex.value == index }
            ?: return
        publish(index, base.copy(isRefreshing = refreshing))
    }

    // Dispatchers.Default a propósito, no el Main.immediate por defecto de
    // viewModelScope: los tests de ViewModel reemplazan Dispatchers.Main por
    // un TestDispatcher compartido, y un while(true)+delay ahí nunca deja
    // vacía la cola del scheduler de prueba (runTest se queda esperando para
    // siempre). En Default corre con tiempo real, fuera del scheduler virtual.
    private fun startAutoRefresh() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                refresh()
            }
        }
    }

    // ── Ruta mock ─────────────────────────────────────────────────────────────

    // Reactiva: se actualiza cuando el usuario hace un pick o cuando simulateGamesStarted cambia.
    private fun observeMockPicks() {
        combine(
            preferencesRepository.preferencesFlow,
            mockSessionRepository.sessionFlow
        ) { prefs, session ->
            val games = if (prefs.simulateGamesStarted)
                MockDataProvider.applyScores(MockDataProvider.MOCK_GAMES, session.simulatedScores)
            else
                MockDataProvider.MOCK_GAMES

            val items = games.map { game ->
                GamePickItem(
                    game       = game,
                    pickedTeam = session.realUserPicks[game.id],
                    isLocked   = prefs.simulateGamesStarted  // bloqueado si se está simulando
                )
            }
            Pair(items, MockDataProvider.MOCK_WEEK_ID)
        }
            .onEach { (items, weekId) ->
                _uiState.value = PickUiState.Success(items, weekId)
            }
            .catch { e ->
                _uiState.value = PickUiState.Error(
                    e.message ?: "Error al cargar partidos de prueba"
                )
            }
            .launchIn(viewModelScope)
    }

    // ── Submit pick (bifurcado) ───────────────────────────────────────────────

    /**
     * Envía el pick para [gameId] con [teamAbbr].
     * - Ruta mock: escribe en MockSessionRepository (flow reactivo actualiza la UI).
     * - Ruta real: update optimista + escritura Firestore con revert en caso de error.
     */
    fun submitPick(gameId: String, teamAbbr: String, kickoffTime: Long, status: GameStatus) {
        val userId = userRepository.getCurrentUser()?.uid ?: return

        if (groupId == MockDataProvider.MOCK_GROUP_ID) {
            viewModelScope.launch {
                mockSessionRepository.saveRealUserPick(gameId, teamAbbr)
                // observeMockPicks() recibe la emisión del sessionFlow y actualiza la UI.
            }
            return
        }

        // Se fija el tab objetivo al momento del tap: si el usuario cambia de
        // semana antes de que la escritura termine (o falle), el update
        // optimista y su revert deben seguir aplicando a la semana donde se
        // hizo el pick, no a la que esté seleccionada en ese momento.
        val targetIndex = _selectedWeekIndex.value
        val currentState = _uiState.value as? PickUiState.Success ?: return
        val previousItems = currentState.items
        val weekId = currentState.weekId

        publish(
            targetIndex,
            currentState.copy(
                items = currentState.items.map { item ->
                    if (item.game.id == gameId) item.copy(pickedTeam = teamAbbr) else item
                }
            )
        )

        viewModelScope.launch {
            try {
                submitPickUseCase(
                    groupId     = groupId,
                    weekId      = weekId,
                    userId      = userId,
                    gameId      = gameId,
                    teamAbbr    = teamAbbr,
                    kickoffTime = kickoffTime,
                    status      = status
                )
                logger.logEvent(AppEvent.PickSubmitted(groupId, weekId, gameId, teamAbbr))
            } catch (e: Exception) {
                weekCache[targetIndex]?.let { cached ->
                    publish(targetIndex, cached.copy(items = previousItems))
                }
                _errorMessage.value = e.message ?: "No se pudo guardar tu pick"
            }
        }
    }

    fun onErrorShown() {
        _errorMessage.value = null
    }

    fun triggerSync() {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) return
        viewModelScope.launch {
            try {
                scoreWeekPicksUseCase(groupId)
                loadWeek(_selectedWeekIndex.value, showLoading = _uiState.value !is PickUiState.Success)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error al sincronizar"
            }
        }
    }
}
